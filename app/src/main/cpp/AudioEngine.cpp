#include "AudioEngine.h"
#include "WavIo.h"
#include <android/log.h>
#include <algorithm>
#include <cstdio>
#include <cstdint>
#include <cmath>

#define LOG_TAG "AudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    mStore = std::make_unique<SampleStore>(mSampleRate * kMaxSeconds);
    mChain.prepare((float) mSampleRate);
    mScratch.assign(4096, 0.0f);
    for (auto& v : mVoices) {
        v.env.configure((float) mSampleRate, 3.0f, 8.0f);
    }
}

AudioEngine::~AudioEngine() {
    stopRecording();
    stopPlayback();
}

SampleBuffer* AudioEngine::selectedSample() const {
    return mStore->get(mSelectedSampleId);
}

int AudioEngine::getSampleLength() const {
    SampleBuffer* s = selectedSample();
    return s ? s->length() : 0;
}

std::vector<float> AudioEngine::getWaveform(int numPoints) const {
    SampleBuffer* s = selectedSample();
    if (!s) return {};
    return s->getWaveform(numPoints);
}

// ---------------------------------------------------------------------------
// Enregistrement
// ---------------------------------------------------------------------------

bool AudioEngine::startRecording() {
    if (mRecording.load()) return false;
    stopPlayback();

    mStore->captureSample()->clear();
    mSelectedSampleId = 0;
    mChain.reset();

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(mSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setInputPreset(oboe::InputPreset::Unprocessed)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = builder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGE("Échec ouverture stream d'entrée: %s", oboe::convertToText(result));
        return false;
    }
    mRecording.store(true);
    result = mInputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Échec démarrage enregistrement: %s", oboe::convertToText(result));
        mRecording.store(false);
        mInputStream->close();
        mInputStream.reset();
        return false;
    }
    return true;
}

void AudioEngine::stopRecording() {
    if (!mInputStream) return;
    mRecording.store(false);
    mInputStream->requestStop();
    mInputStream->close();
    mInputStream.reset();
    mStore->captureSample()->resetTrim();
    mStore->assignPad(0, 0); // pad 0 = sample de capture
}

// ---------------------------------------------------------------------------
// Lecture / déclenchement
// ---------------------------------------------------------------------------

bool AudioEngine::ensureOutputStream() {
    if (mOutputStream) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setSampleRate(mSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setUsage(oboe::Usage::Media)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = builder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Échec ouverture stream de sortie: %s", oboe::convertToText(result));
        mOutputStream.reset();
        return false;
    }
    result = mOutputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Échec démarrage lecture: %s", oboe::convertToText(result));
        mOutputStream->close();
        mOutputStream.reset();
        return false;
    }
    return true;
}

bool AudioEngine::startPlayback() {
    if (mRecording.load()) return false;
    SampleBuffer* s = selectedSample();
    if (!s || s->length() <= 0) return false;
    if (!ensureOutputStream()) return false;

    NoteEvent e;
    e.type = NoteEvent::NOTE_ON;
    e.padId = -1;
    e.sampleId = mSelectedSampleId;
    e.pitchRatio = 1.0f;
    e.gain = 1.0f;
    e.triggerMode = mLoop.load() ? TRIG_LOOP : TRIG_ONESHOT;
    e.useGlobalLoop = true;
    mPlaying.store(true);
    mEvents.push(e);
    return true;
}

void AudioEngine::triggerPad(int padId, float pitchRatio, float gain, int triggerMode) {
    if (mRecording.load()) return;
    int sampleId = mStore->padSample(padId);
    if (sampleId < 0) return;
    SampleBuffer* s = mStore->get(sampleId);
    if (!s || s->length() <= 0) return;
    if (!ensureOutputStream()) return;

    NoteEvent e;
    e.type = NoteEvent::NOTE_ON;
    e.padId = padId;
    e.sampleId = sampleId;
    e.pitchRatio = pitchRatio;
    e.gain = gain;
    e.triggerMode = triggerMode;
    e.useGlobalLoop = false;
    mPlaying.store(true);
    mEvents.push(e);
}

void AudioEngine::releasePad(int padId) {
    NoteEvent e;
    e.type = NoteEvent::NOTE_OFF;
    e.padId = padId;
    mEvents.push(e);
}

void AudioEngine::stopPlayback() {
    mPlaying.store(false);
    for (auto& v : mVoices) v.active = false;
    mActiveVoices.store(0);
    mPrimaryReadPos.store(0);
    if (!mOutputStream) return;
    mOutputStream->requestStop();
    mOutputStream->close();
    mOutputStream.reset();
}

int AudioEngine::allocateVoice() {
    // Voix libre en priorité
    for (int i = 0; i < kNumVoices; ++i) {
        if (!mVoices[i].active) return i;
    }
    // Sinon, vole la plus ancienne
    int oldest = 0;
    uint64_t minOrder = mVoices[0].order;
    for (int i = 1; i < kNumVoices; ++i) {
        if (mVoices[i].order < minOrder) { minOrder = mVoices[i].order; oldest = i; }
    }
    return oldest;
}

void AudioEngine::processEvents() {
    NoteEvent e;
    while (mEvents.pop(e)) {
        if (e.type == NoteEvent::NOTE_ON) {
            SampleBuffer* s = mStore->get(e.sampleId);
            if (!s || s->length() <= 0) continue;
            int idx = allocateVoice();
            Voice& v = mVoices[idx];
            v.active = true;
            v.sample = s;
            v.readPos = (double) s->trimStart();
            v.pitchRatio = e.pitchRatio > 0.0f ? e.pitchRatio : 1.0;
            v.gain = e.gain;
            v.triggerMode = e.triggerMode;
            v.useGlobalLoop = e.useGlobalLoop;
            v.padId = e.padId;
            v.order = ++mOrderCounter;
            v.env.noteOn();
        } else { // NOTE_OFF : release des voix du pad (mode gate)
            for (auto& v : mVoices) {
                if (v.active && v.padId == e.padId) v.env.noteOff();
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Édition
// ---------------------------------------------------------------------------

void AudioEngine::clearSample() { mStore->captureSample()->clear(); }
void AudioEngine::reverseSample() { if (!isPlaying()) { SampleBuffer* s = selectedSample(); if (s) s->reverse(); } }
void AudioEngine::normalizeSample() { if (!isPlaying()) { SampleBuffer* s = selectedSample(); if (s) s->normalize(); } }
void AudioEngine::setTrim(int start, int end) { SampleBuffer* s = selectedSample(); if (s) s->setTrim(start, end); }
void AudioEngine::resetTrim() { SampleBuffer* s = selectedSample(); if (s) s->resetTrim(); }

// ---------------------------------------------------------------------------
// Export WAV
// ---------------------------------------------------------------------------

namespace {
    void writeU32(FILE* f, uint32_t v) { fwrite(&v, 4, 1, f); }
    void writeU16(FILE* f, uint16_t v) { fwrite(&v, 2, 1, f); }
}

bool AudioEngine::writeWav(FILE* f, int sampleId, bool applyEffects, bool useTrim) {
    if (!f) return false;
    SampleBuffer* s = mStore->get(sampleId);
    if (!s) { fclose(f); return false; }
    int start = useTrim ? s->trimStart() : 0;
    int end = useTrim ? s->trimEnd() : s->length();
    int total = end - start;
    if (total <= 0) {
        fclose(f);
        return false;
    }

    const uint16_t channels = 1;
    const uint16_t bitsPerSample = 16;
    const uint32_t sampleRate = (uint32_t) mSampleRate;
    const uint32_t byteRate = sampleRate * channels * bitsPerSample / 8;
    const uint16_t blockAlign = channels * bitsPerSample / 8;
    const uint32_t dataBytes = (uint32_t) total * blockAlign;

    fwrite("RIFF", 1, 4, f);
    writeU32(f, 36 + dataBytes);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    writeU32(f, 16);
    writeU16(f, 1); // PCM
    writeU16(f, channels);
    writeU32(f, sampleRate);
    writeU32(f, byteRate);
    writeU16(f, blockAlign);
    writeU16(f, bitsPerSample);
    fwrite("data", 1, 4, f);
    writeU32(f, dataBytes);

    // Rendu par blocs (avec ou sans effets)
    if (applyEffects) mChain.reset();
    const int kBlock = 512;
    std::vector<float> block(kBlock);
    std::vector<int16_t> pcm(kBlock);
    int pos = start;
    while (pos < end) {
        int n = std::min(kBlock, end - pos);
        for (int i = 0; i < n; ++i) block[i] = s->at(pos + i);
        if (applyEffects) mChain.process(block.data(), n);
        for (int i = 0; i < n; ++i) {
            float v = std::max(-1.0f, std::min(1.0f, block[i]));
            pcm[i] = (int16_t) std::lround(v * 32767.0f);
        }
        fwrite(pcm.data(), sizeof(int16_t), n, f);
        pos += n;
    }
    if (applyEffects) mChain.reset();
    fclose(f);
    return true;
}

bool AudioEngine::exportWav(const char* path) {
    if (isPlaying() || mRecording.load()) return false;
    FILE* f = fopen(path, "wb");
    if (!f) {
        LOGE("Impossible d'ouvrir le fichier d'export: %s", path);
        return false;
    }
    return writeWav(f, mSelectedSampleId, true, true);
}

bool AudioEngine::exportWavFd(int fd) {
    if (isPlaying() || mRecording.load()) return false;
    FILE* f = fdopen(fd, "wb");
    if (!f) {
        LOGE("Impossible d'ouvrir le descripteur d'export (fd=%d)", fd);
        return false;
    }
    return writeWav(f, mSelectedSampleId, true, true);
}

bool AudioEngine::saveCaptureToFd(int fd) {
    if (mRecording.load()) return false;
    FILE* f = fdopen(fd, "wb");
    if (!f) {
        LOGE("Impossible d'ouvrir le descripteur de sauvegarde (fd=%d)", fd);
        return false;
    }
    // Capture = sample id 0, brut (sans effets) et complet (sans trim).
    return writeWav(f, 0, false, false);
}

int AudioEngine::loadWavFd(int fd) {
    FILE* f = fdopen(fd, "rb");
    if (!f) {
        LOGE("Impossible d'ouvrir le descripteur de lecture (fd=%d)", fd);
        return -1;
    }
    std::vector<float> data = WavIo::readWavMono16(f); // ferme f
    if (data.empty()) return -1;
    return mStore->addSample(SampleBuffer::fromData(data));
}

// ---------------------------------------------------------------------------
// Callbacks Oboe
// ---------------------------------------------------------------------------

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream* stream, void* audioData,
                                                   int32_t numFrames) {
    if (stream->getDirection() == oboe::Direction::Input) {
        return onRecord(audioData, numFrames);
    }
    return onPlay(stream, audioData, numFrames);
}

oboe::DataCallbackResult AudioEngine::onRecord(void* audioData, int32_t numFrames) {
    auto* in = static_cast<float*>(audioData);
    SampleBuffer* cap = mStore->captureSample();
    cap->appendRealtime(in, numFrames);
    if (cap->length() >= cap->capacity()) {
        return oboe::DataCallbackResult::Stop;
    }
    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult AudioEngine::onPlay(oboe::AudioStream* stream, void* audioData,
                                             int32_t numFrames) {
    int channels = stream->getChannelCount();
    auto* out = static_cast<float*>(audioData);

    if ((int) mScratch.size() < numFrames) mScratch.resize(numFrames);
    std::fill(mScratch.begin(), mScratch.begin() + numFrames, 0.0f);

    processEvents();

    SampleBuffer* sel = selectedSample();
    bool globalLoop = mLoop.load();

    // Mixage des voix
    for (auto& v : mVoices) {
        if (!v.active || v.sample == nullptr) continue;
        SampleBuffer* s = v.sample;
        int start = s->trimStart();
        int end = s->trimEnd();
        if (end <= start) { v.active = false; continue; }
        bool loop = v.useGlobalLoop ? globalLoop : (v.triggerMode == TRIG_LOOP);

        for (int i = 0; i < numFrames; ++i) {
            if (v.readPos >= (double) end) {
                if (loop) {
                    v.readPos = (double) start;
                } else {
                    v.env.noteOff();
                }
            }
            float e = v.env.next();
            float smp = s->atInterp(v.readPos);
            mScratch[i] += smp * e * v.gain;
            v.readPos += v.pitchRatio;
            if (!v.env.isActive()) { v.active = false; break; }
        }
    }

    // Chaîne d'effets master
    mChain.process(mScratch.data(), numFrames);

    // Écriture entrelacée (mono -> N canaux)
    for (int i = 0; i < numFrames; ++i) {
        float val = mScratch[i];
        for (int c = 0; c < channels; ++c) {
            out[i * channels + c] = val;
        }
    }

    // Mise à jour des compteurs (voix actives + tête de lecture représentative)
    int activeCount = 0;
    int primary = 0;
    bool primaryFound = false;
    for (auto& v : mVoices) {
        if (!v.active) continue;
        activeCount++;
        if (!primaryFound && v.sample == sel) {
            primary = (int) v.readPos;
            primaryFound = true;
        }
    }
    mActiveVoices.store(activeCount);
    mPrimaryReadPos.store(primary);
    mPlaying.store(activeCount > 0);

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Stream fermé sur erreur: %s", oboe::convertToText(error));
    if (stream->getDirection() == oboe::Direction::Input) {
        mRecording.store(false);
    } else {
        for (auto& v : mVoices) v.active = false;
        mActiveVoices.store(0);
        mPlaying.store(false);
    }
}

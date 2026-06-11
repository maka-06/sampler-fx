#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cstdio>
#include <cstdint>
#include <cmath>

#define LOG_TAG "AudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    mSample = std::make_unique<SampleBuffer>(mSampleRate * kMaxSeconds);
    mChain.prepare((float) mSampleRate);
    mScratch.assign(4096, 0.0f);
}

AudioEngine::~AudioEngine() {
    stopRecording();
    stopPlayback();
}

bool AudioEngine::startRecording() {
    if (mRecording.load() || mPlaying.load()) return false;

    mSample->clear();
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
    mSample->resetTrim();
}

bool AudioEngine::startPlayback() {
    if (mPlaying.load() || mRecording.load()) return false;
    if (mSample->length() <= 0) return false;

    mChain.reset();
    mPlayHead.store(mSample->trimStart());

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
        return false;
    }
    mPlaying.store(true);
    result = mOutputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Échec démarrage lecture: %s", oboe::convertToText(result));
        mPlaying.store(false);
        mOutputStream->close();
        mOutputStream.reset();
        return false;
    }
    return true;
}

void AudioEngine::stopPlayback() {
    if (!mOutputStream) return;
    mPlaying.store(false);
    mOutputStream->requestStop();
    mOutputStream->close();
    mOutputStream.reset();
}

void AudioEngine::clearSample() { mSample->clear(); }
void AudioEngine::reverseSample() { if (!mPlaying.load()) mSample->reverse(); }
void AudioEngine::normalizeSample() { if (!mPlaying.load()) mSample->normalize(); }
void AudioEngine::setTrim(int start, int end) { mSample->setTrim(start, end); }
void AudioEngine::resetTrim() { mSample->resetTrim(); }

namespace {
    void writeU32(FILE* f, uint32_t v) { fwrite(&v, 4, 1, f); }
    void writeU16(FILE* f, uint16_t v) { fwrite(&v, 2, 1, f); }
}

bool AudioEngine::exportWav(const char* path) {
    if (mPlaying.load() || mRecording.load()) return false;
    int start = mSample->trimStart();
    int end = mSample->trimEnd();
    int total = end - start;
    if (total <= 0) return false;

    FILE* f = fopen(path, "wb");
    if (!f) {
        LOGE("Impossible d'ouvrir le fichier d'export: %s", path);
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

    // Rendu par blocs à travers la chaîne d'effets
    mChain.reset();
    const int kBlock = 512;
    std::vector<float> block(kBlock);
    std::vector<int16_t> pcm(kBlock);
    int pos = start;
    while (pos < end) {
        int n = std::min(kBlock, end - pos);
        for (int i = 0; i < n; ++i) block[i] = mSample->at(pos + i);
        mChain.process(block.data(), n);
        for (int i = 0; i < n; ++i) {
            float s = std::max(-1.0f, std::min(1.0f, block[i]));
            pcm[i] = (int16_t) std::lround(s * 32767.0f);
        }
        fwrite(pcm.data(), sizeof(int16_t), n, f);
        pos += n;
    }
    mChain.reset();
    fclose(f);
    return true;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream* stream, void* audioData,
                                                   int32_t numFrames) {
    if (stream->getDirection() == oboe::Direction::Input) {
        return onRecord(audioData, numFrames);
    }
    return onPlay(stream, audioData, numFrames);
}

oboe::DataCallbackResult AudioEngine::onRecord(void* audioData, int32_t numFrames) {
    auto* in = static_cast<float*>(audioData);
    mSample->appendRealtime(in, numFrames);
    // Arrête automatiquement si le buffer est plein
    if (mSample->length() >= mSample->capacity()) {
        return oboe::DataCallbackResult::Stop;
    }
    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult AudioEngine::onPlay(oboe::AudioStream* stream, void* audioData,
                                             int32_t numFrames) {
    int channels = stream->getChannelCount();
    auto* out = static_cast<float*>(audioData);

    if ((int) mScratch.size() < numFrames) mScratch.resize(numFrames);

    int head = mPlayHead.load();
    int start = mSample->trimStart();
    int end = mSample->trimEnd();
    bool loop = mLoop.load();
    bool reachedEnd = false;

    for (int i = 0; i < numFrames; ++i) {
        float s = 0.0f;
        if (head < end) {
            s = mSample->at(head);
            head++;
        } else {
            if (loop) {
                head = start;
                if (head < end) { s = mSample->at(head); head++; }
            } else {
                reachedEnd = true;
            }
        }
        mScratch[i] = s;
    }

    // Chaîne d'effets sur le bloc mono
    mChain.process(mScratch.data(), numFrames);

    // Écriture entrelacée vers la sortie
    for (int i = 0; i < numFrames; ++i) {
        float v = mScratch[i];
        for (int c = 0; c < channels; ++c) {
            out[i * channels + c] = v;
        }
    }

    mPlayHead.store(head);

    if (reachedEnd && !loop) {
        mPlaying.store(false);
        return oboe::DataCallbackResult::Stop;
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Stream fermé sur erreur: %s", oboe::convertToText(error));
    if (stream->getDirection() == oboe::Direction::Input) {
        mRecording.store(false);
    } else {
        mPlaying.store(false);
    }
}

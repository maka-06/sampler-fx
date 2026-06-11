#ifndef SAMPLEAPP_AUDIOENGINE_H
#define SAMPLEAPP_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <memory>
#include <atomic>
#include <array>
#include <vector>
#include <cstdio>
#include "SampleBuffer.h"
#include "SampleStore.h"
#include "Voice.h"
#include "LockFreeQueue.h"
#include "effects/EffectChain.h"

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool startRecording();
    void stopRecording();
    // Lecture du sample sélectionné via une voix (suit le flag loop global).
    bool startPlayback();
    void stopPlayback();

    // Déclenchement polyphonique (pads / clavier).
    void triggerPad(int padId, float pitchRatio, float gain, int triggerMode);
    void releasePad(int padId);

    void setLoop(bool loop) { mLoop.store(loop); }
    bool isRecording() const { return mRecording.load(); }
    bool isPlaying() const { return mPlaying.load(); }

    // Édition du sample sélectionné
    void clearSample();
    void reverseSample();
    void normalizeSample();
    void setTrim(int start, int end);
    void resetTrim();

    bool exportWav(const char* path);
    bool exportWavFd(int fd);
    // Sauve la capture brute et complète (sans effets) vers un fd -> pour la bibliothèque.
    bool saveCaptureToFd(int fd);
    // Charge un WAV (mono 16-bit) depuis un fd dans le store. Renvoie le sampleId ou -1.
    int loadWavFd(int fd);

    // Pads / sélection
    void setSelectedSample(int sampleId) { mSelectedSampleId = sampleId; }
    void assignPadSample(int pad, int sampleId) { mStore->assignPad(pad, sampleId); }

    // Effets
    void setEffectEnabled(int effectId, bool enabled) { mChain.setEnabled(effectId, enabled); }
    void setEffectParam(int effectId, int paramId, float value) { mChain.setParam(effectId, paramId, value); }

    // Infos / accès
    int getSampleLength() const;
    int getSampleRate() const { return mSampleRate; }
    int getPlayHead() const { return mPrimaryReadPos.load(); }
    int getSampleCount() const { return mStore->sampleCount(); }
    std::vector<float> getWaveform(int numPoints) const;

    // Callbacks Oboe
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    oboe::DataCallbackResult onRecord(void* audioData, int32_t numFrames);
    oboe::DataCallbackResult onPlay(oboe::AudioStream* stream, void* audioData, int32_t numFrames);
    // Écrit un sample dans f puis ferme f. applyEffects : passe par la chaîne master.
    // useTrim : limite à la région de trim, sinon le sample entier.
    bool writeWav(FILE* f, int sampleId, bool applyEffects, bool useTrim);

    SampleBuffer* selectedSample() const;
    bool ensureOutputStream();
    void processEvents();
    int allocateVoice(); // renvoie l'index d'une voix libre ou volée

    static constexpr int kMaxSeconds = 60;
    static constexpr int kNumVoices = 16;
    int mSampleRate = 48000;

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::unique_ptr<SampleStore> mStore;
    int mSelectedSampleId = 0;
    EffectChain mChain;

    std::array<Voice, kNumVoices> mVoices;
    LockFreeQueue<NoteEvent, 64> mEvents;
    uint64_t mOrderCounter = 0;

    std::atomic<bool> mRecording{false};
    std::atomic<bool> mLoop{false};
    std::atomic<bool> mPlaying{false};
    std::atomic<int> mActiveVoices{0};
    std::atomic<int> mPrimaryReadPos{0};

    std::vector<float> mScratch; // bloc mono temporaire pour le mixage
};

#endif // SAMPLEAPP_AUDIOENGINE_H

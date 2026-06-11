#ifndef SAMPLEAPP_AUDIOENGINE_H
#define SAMPLEAPP_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <memory>
#include <atomic>
#include <vector>
#include "SampleBuffer.h"
#include "effects/EffectChain.h"

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool startRecording();
    void stopRecording();
    bool startPlayback();
    void stopPlayback();

    void setLoop(bool loop) { mLoop.store(loop); }
    bool isRecording() const { return mRecording.load(); }
    bool isPlaying() const { return mPlaying.load(); }

    // Édition du sample
    void clearSample();
    void reverseSample();
    void normalizeSample();
    void setTrim(int start, int end);
    void resetTrim();

    // Rend la région courante à travers la chaîne d'effets et l'écrit en WAV 16-bit.
    bool exportWav(const char* path);

    // Effets
    void setEffectEnabled(int effectId, bool enabled) { mChain.setEnabled(effectId, enabled); }
    void setEffectParam(int effectId, int paramId, float value) { mChain.setParam(effectId, paramId, value); }

    // Infos / accès
    int getSampleLength() const { return mSample->length(); }
    int getSampleRate() const { return mSampleRate; }
    int getPlayHead() const { return mPlayHead.load(); }
    std::vector<float> getWaveform(int numPoints) const { return mSample->getWaveform(numPoints); }
    SampleBuffer* sample() { return mSample.get(); }

    // Callbacks Oboe
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    oboe::DataCallbackResult onRecord(void* audioData, int32_t numFrames);
    oboe::DataCallbackResult onPlay(oboe::AudioStream* stream, void* audioData, int32_t numFrames);

    static constexpr int kMaxSeconds = 60;
    int mSampleRate = 48000;

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::unique_ptr<SampleBuffer> mSample;
    EffectChain mChain;

    std::atomic<bool> mRecording{false};
    std::atomic<bool> mPlaying{false};
    std::atomic<bool> mLoop{false};
    std::atomic<int> mPlayHead{0};

    std::vector<float> mScratch; // bloc mono temporaire pour la lecture
};

#endif // SAMPLEAPP_AUDIOENGINE_H

#ifndef SAMPLEAPP_EFFECT_H
#define SAMPLEAPP_EFFECT_H

#include <atomic>

// Interface commune à tous les effets temps réel.
// process() traite un bloc mono "en place".
class Effect {
public:
    virtual ~Effect() = default;

    virtual void prepare(float sampleRate) { mSampleRate = sampleRate; reset(); }
    virtual void process(float* buffer, int numFrames) = 0;
    virtual void reset() {}
    virtual void setParam(int paramId, float value) = 0;

    void setEnabled(bool e) { mEnabled.store(e); }
    bool isEnabled() const { return mEnabled.load(); }

protected:
    float mSampleRate = 48000.0f;
    std::atomic<bool> mEnabled{false};
};

#endif // SAMPLEAPP_EFFECT_H

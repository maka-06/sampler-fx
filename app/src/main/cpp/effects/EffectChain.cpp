#include "EffectChain.h"

void EffectChain::prepare(float sampleRate) {
    mFilter.prepare(sampleRate);
    mDistortion.prepare(sampleRate);
    mPitch.prepare(sampleRate);
    mDelay.prepare(sampleRate);
    mReverb.prepare(sampleRate);
}

void EffectChain::reset() {
    mFilter.reset();
    mDistortion.reset();
    mPitch.reset();
    mDelay.reset();
    mReverb.reset();
}

Effect* EffectChain::get(int effectId) {
    switch (effectId) {
        case FX_FILTER: return &mFilter;
        case FX_DISTORTION: return &mDistortion;
        case FX_PITCH: return &mPitch;
        case FX_DELAY: return &mDelay;
        case FX_REVERB: return &mReverb;
        default: return nullptr;
    }
}

void EffectChain::setEnabled(int effectId, bool enabled) {
    Effect* e = get(effectId);
    if (e) e->setEnabled(enabled);
}

void EffectChain::setParam(int effectId, int paramId, float value) {
    Effect* e = get(effectId);
    if (e) e->setParam(paramId, value);
}

void EffectChain::process(float* buffer, int numFrames) {
    // Ordre fixe de la chaîne
    mFilter.process(buffer, numFrames);
    mDistortion.process(buffer, numFrames);
    mPitch.process(buffer, numFrames);
    mDelay.process(buffer, numFrames);
    mReverb.process(buffer, numFrames);
}

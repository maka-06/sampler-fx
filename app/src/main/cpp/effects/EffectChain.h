#ifndef SAMPLEAPP_EFFECTCHAIN_H
#define SAMPLEAPP_EFFECTCHAIN_H

#include "Effect.h"
#include "Biquad.h"
#include "Distortion.h"
#include "PitchShifter.h"
#include "Delay.h"
#include "Reverb.h"
#include "../Params.h"
#include <memory>

// Chaîne d'effets à ordre fixe : Filtre -> Distortion -> Pitch -> Delay -> Reverb.
class EffectChain {
public:
    void prepare(float sampleRate);
    void process(float* buffer, int numFrames);
    void reset();

    void setEnabled(int effectId, bool enabled);
    void setParam(int effectId, int paramId, float value);

private:
    Effect* get(int effectId);

    Biquad mFilter;
    Distortion mDistortion;
    PitchShifter mPitch;
    Delay mDelay;
    Reverb mReverb;
};

#endif // SAMPLEAPP_EFFECTCHAIN_H

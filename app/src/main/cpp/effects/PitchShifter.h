#ifndef SAMPLEAPP_PITCHSHIFTER_H
#define SAMPLEAPP_PITCHSHIFTER_H

#include "Effect.h"
#include <atomic>
#include <vector>

// Pitch shifter temps réel dans le domaine temporel (delay-line à deux taps
// avec crossfade), qui change la hauteur sans modifier la durée.
class PitchShifter : public Effect {
public:
    void prepare(float sampleRate) override;
    void process(float* buffer, int numFrames) override;
    void reset() override;
    void setParam(int paramId, float value) override;

private:
    float readInterp(float delaySamples) const;

    std::vector<float> mBuf;
    int mWriteIdx = 0;
    int mWindow = 2048;
    float mRd = 0.0f; // décalage de lecture courant (0..mWindow)

    std::atomic<float> mSemitones{0.0f};
};

#endif // SAMPLEAPP_PITCHSHIFTER_H

#ifndef SAMPLEAPP_BIQUAD_H
#define SAMPLEAPP_BIQUAD_H

#include "Effect.h"
#include <atomic>

// Filtre biquad passe-bas / passe-haut (coefficients Robert Bristow-Johnson).
class Biquad : public Effect {
public:
    void prepare(float sampleRate) override;
    void process(float* buffer, int numFrames) override;
    void reset() override;
    void setParam(int paramId, float value) override;

private:
    void recompute();

    std::atomic<int> mType{0};        // 0 = LP, 1 = HP
    std::atomic<float> mCutoff{2000.0f};
    std::atomic<float> mQ{0.707f};

    // Coefficients
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    // États
    float z1 = 0, z2 = 0;
    bool mDirty = true;
};

#endif // SAMPLEAPP_BIQUAD_H

#include "Biquad.h"
#include "../Params.h"
#include <cmath>

void Biquad::prepare(float sampleRate) {
    mSampleRate = sampleRate;
    reset();
    mDirty = true;
}

void Biquad::reset() {
    z1 = 0;
    z2 = 0;
}

void Biquad::setParam(int paramId, float value) {
    switch (paramId) {
        case P_FILTER_TYPE: mType.store((int) value); break;
        case P_FILTER_CUTOFF: mCutoff.store(value); break;
        case P_FILTER_Q: mQ.store(value); break;
        default: return;
    }
    mDirty = true;
}

void Biquad::recompute() {
    float cutoff = mCutoff.load();
    float q = mQ.load();
    if (cutoff < 20.0f) cutoff = 20.0f;
    if (cutoff > mSampleRate * 0.45f) cutoff = mSampleRate * 0.45f;
    if (q < 0.1f) q = 0.1f;

    float w0 = 2.0f * (float) M_PI * cutoff / mSampleRate;
    float cosw0 = std::cos(w0);
    float sinw0 = std::sin(w0);
    float alpha = sinw0 / (2.0f * q);

    float a0;
    if (mType.load() == 0) { // passe-bas
        b0 = (1.0f - cosw0) / 2.0f;
        b1 = 1.0f - cosw0;
        b2 = (1.0f - cosw0) / 2.0f;
        a0 = 1.0f + alpha;
        a1 = -2.0f * cosw0;
        a2 = 1.0f - alpha;
    } else { // passe-haut
        b0 = (1.0f + cosw0) / 2.0f;
        b1 = -(1.0f + cosw0);
        b2 = (1.0f + cosw0) / 2.0f;
        a0 = 1.0f + alpha;
        a1 = -2.0f * cosw0;
        a2 = 1.0f - alpha;
    }
    // Normalisation par a0
    b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0;
    mDirty = false;
}

void Biquad::process(float* buffer, int numFrames) {
    if (!mEnabled.load()) return;
    if (mDirty) recompute();

    for (int i = 0; i < numFrames; ++i) {
        float x = buffer[i];
        // Forme directe transposée II
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        buffer[i] = y;
    }
}

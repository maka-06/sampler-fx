#include "PitchShifter.h"
#include "../Params.h"
#include <cmath>

void PitchShifter::prepare(float sampleRate) {
    mSampleRate = sampleRate;
    mWindow = (int) (sampleRate * 0.045f); // ~45 ms
    if (mWindow < 256) mWindow = 256;
    mBuf.assign((size_t) mWindow * 2, 0.0f);
    mWriteIdx = 0;
    mRd = 0.0f;
}

void PitchShifter::reset() {
    std::fill(mBuf.begin(), mBuf.end(), 0.0f);
    mWriteIdx = 0;
    mRd = 0.0f;
}

void PitchShifter::setParam(int paramId, float value) {
    if (paramId == P_PITCH_SEMITONES) mSemitones.store(value);
}

float PitchShifter::readInterp(float delaySamples) const {
    int size = (int) mBuf.size();
    float pos = (float) mWriteIdx - delaySamples;
    while (pos < 0) pos += size;
    while (pos >= size) pos -= size;
    int i0 = (int) pos;
    int i1 = (i0 + 1) % size;
    float frac = pos - (float) i0;
    return mBuf[i0] * (1.0f - frac) + mBuf[i1] * frac;
}

void PitchShifter::process(float* buffer, int numFrames) {
    if (!mEnabled.load() || mBuf.empty()) return;

    float semis = mSemitones.load();
    if (std::fabs(semis) < 0.01f) {
        // Pas de transposition : on alimente quand même la ligne pour rester cohérent
        for (int i = 0; i < numFrames; ++i) {
            mBuf[mWriteIdx] = buffer[i];
            if (++mWriteIdx >= (int) mBuf.size()) mWriteIdx = 0;
        }
        return;
    }

    float ratio = std::pow(2.0f, semis / 12.0f);
    float rate = ratio - 1.0f; // vitesse de dérive du pointeur de lecture
    float W = (float) mWindow;
    const float PI = (float) M_PI;

    for (int i = 0; i < numFrames; ++i) {
        mBuf[mWriteIdx] = buffer[i];
        if (++mWriteIdx >= (int) mBuf.size()) mWriteIdx = 0;

        mRd += rate;
        while (mRd >= W) mRd -= W;
        while (mRd < 0) mRd += W;

        float rd2 = mRd + W * 0.5f;
        if (rd2 >= W) rd2 -= W;

        float g1 = std::sin(PI * (mRd / W));
        float g2 = std::sin(PI * (rd2 / W));

        float s1 = readInterp(mRd);
        float s2 = readInterp(rd2);
        buffer[i] = s1 * g1 + s2 * g2;
    }
}

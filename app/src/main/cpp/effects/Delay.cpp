#include "Delay.h"
#include "../Params.h"

void Delay::prepare(float sampleRate) {
    mSampleRate = sampleRate;
    // Ligne de 2 secondes max
    mLine.assign((size_t) (sampleRate * 2.0f), 0.0f);
    mWriteIdx = 0;
}

void Delay::reset() {
    std::fill(mLine.begin(), mLine.end(), 0.0f);
    mWriteIdx = 0;
}

void Delay::setParam(int paramId, float value) {
    switch (paramId) {
        case P_DELAY_TIME: mTimeMs.store(value); break;
        case P_DELAY_FEEDBACK: mFeedback.store(value); break;
        case P_DELAY_MIX: mMix.store(value); break;
        default: break;
    }
}

void Delay::process(float* buffer, int numFrames) {
    if (!mEnabled.load() || mLine.empty()) return;

    int size = (int) mLine.size();
    int delaySamples = (int) (mTimeMs.load() * 0.001f * mSampleRate);
    if (delaySamples < 1) delaySamples = 1;
    if (delaySamples >= size) delaySamples = size - 1;

    float fb = mFeedback.load();
    float mix = mMix.load();

    for (int i = 0; i < numFrames; ++i) {
        int readIdx = mWriteIdx - delaySamples;
        if (readIdx < 0) readIdx += size;

        float delayed = mLine[readIdx];
        float in = buffer[i];
        // Écrit l'entrée + feedback du signal retardé
        mLine[mWriteIdx] = in + delayed * fb;
        // Mélange dry/wet
        buffer[i] = in * (1.0f - mix) + delayed * mix;

        if (++mWriteIdx >= size) mWriteIdx = 0;
    }
}

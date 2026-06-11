#include "Reverb.h"
#include "../Params.h"
#include <cmath>

// Longueurs de retard d'origine (Freeverb), calibrées pour 44100 Hz.
static const int kCombTuning[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
static const int kAllpassTuning[4] = {556, 441, 341, 225};
static const float kStereoSpread = 0.0f; // version mono

float Reverb::Comb::process(float input, float feedback, float damp) {
    float output = buf[idx];
    filterStore = output * (1.0f - damp) + filterStore * damp;
    buf[idx] = input + filterStore * feedback;
    if (++idx >= (int) buf.size()) idx = 0;
    return output;
}

float Reverb::Allpass::process(float input) {
    float bufout = buf[idx];
    float output = -input + bufout;
    buf[idx] = input + bufout * 0.5f;
    if (++idx >= (int) buf.size()) idx = 0;
    return output;
}

void Reverb::prepare(float sampleRate) {
    mSampleRate = sampleRate;
    float scale = sampleRate / 44100.0f;
    for (int i = 0; i < NUM_COMBS; ++i) {
        int len = (int) ((kCombTuning[i] + (int) kStereoSpread) * scale);
        if (len < 1) len = 1;
        mCombs[i].buf.assign(len, 0.0f);
        mCombs[i].idx = 0;
        mCombs[i].filterStore = 0.0f;
    }
    for (int i = 0; i < NUM_ALLPASS; ++i) {
        int len = (int) (kAllpassTuning[i] * scale);
        if (len < 1) len = 1;
        mAllpass[i].buf.assign(len, 0.0f);
        mAllpass[i].idx = 0;
    }
}

void Reverb::reset() {
    for (auto& c : mCombs) { std::fill(c.buf.begin(), c.buf.end(), 0.0f); c.filterStore = 0.0f; c.idx = 0; }
    for (auto& a : mAllpass) { std::fill(a.buf.begin(), a.buf.end(), 0.0f); a.idx = 0; }
}

void Reverb::setParam(int paramId, float value) {
    switch (paramId) {
        case P_REVERB_ROOMSIZE: mRoomSize.store(value); break;
        case P_REVERB_DAMPING: mDamping.store(value); break;
        case P_REVERB_MIX: mMix.store(value); break;
        default: break;
    }
}

void Reverb::process(float* buffer, int numFrames) {
    if (!mEnabled.load()) return;

    float feedback = 0.28f + mRoomSize.load() * 0.7f; // ~0.28..0.98
    float damp = mDamping.load();
    float mix = mMix.load();
    const float gain = 0.015f;

    for (int i = 0; i < numFrames; ++i) {
        float input = buffer[i] * gain;
        float out = 0.0f;
        for (auto& c : mCombs) out += c.process(input, feedback, damp);
        for (auto& a : mAllpass) out = a.process(out);
        buffer[i] = buffer[i] * (1.0f - mix) + out * mix * 3.0f;
    }
}

#ifndef SAMPLEAPP_REVERB_H
#define SAMPLEAPP_REVERB_H

#include "Effect.h"
#include <atomic>
#include <vector>

// Reverb façon Freeverb (8 filtres en peigne + 4 passe-tout), version mono.
class Reverb : public Effect {
public:
    void prepare(float sampleRate) override;
    void process(float* buffer, int numFrames) override;
    void reset() override;
    void setParam(int paramId, float value) override;

private:
    static constexpr int NUM_COMBS = 8;
    static constexpr int NUM_ALLPASS = 4;

    struct Comb {
        std::vector<float> buf;
        int idx = 0;
        float filterStore = 0.0f;
        float process(float input, float feedback, float damp);
    };
    struct Allpass {
        std::vector<float> buf;
        int idx = 0;
        float process(float input);
    };

    Comb mCombs[NUM_COMBS];
    Allpass mAllpass[NUM_ALLPASS];

    std::atomic<float> mRoomSize{0.7f};
    std::atomic<float> mDamping{0.5f};
    std::atomic<float> mMix{0.3f};
};

#endif // SAMPLEAPP_REVERB_H

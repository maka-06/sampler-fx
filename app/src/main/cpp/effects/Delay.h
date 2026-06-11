#ifndef SAMPLEAPP_DELAY_H
#define SAMPLEAPP_DELAY_H

#include "Effect.h"
#include <atomic>
#include <vector>

// Delay / écho : ligne à retard circulaire avec feedback et mix dry/wet.
class Delay : public Effect {
public:
    void prepare(float sampleRate) override;
    void process(float* buffer, int numFrames) override;
    void reset() override;
    void setParam(int paramId, float value) override;

private:
    std::vector<float> mLine;
    int mWriteIdx = 0;

    std::atomic<float> mTimeMs{300.0f};
    std::atomic<float> mFeedback{0.4f};
    std::atomic<float> mMix{0.3f};
};

#endif // SAMPLEAPP_DELAY_H

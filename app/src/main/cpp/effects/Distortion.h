#ifndef SAMPLEAPP_DISTORTION_H
#define SAMPLEAPP_DISTORTION_H

#include "Effect.h"
#include <atomic>

// Distortion par waveshaping (soft-clip tanh).
class Distortion : public Effect {
public:
    void process(float* buffer, int numFrames) override;
    void setParam(int paramId, float value) override;

private:
    std::atomic<float> mDrive{5.0f};  // gain d'entrée
    std::atomic<float> mLevel{0.6f};  // gain de sortie
};

#endif // SAMPLEAPP_DISTORTION_H

#include "Distortion.h"
#include "../Params.h"
#include <cmath>

void Distortion::setParam(int paramId, float value) {
    switch (paramId) {
        case P_DIST_DRIVE: mDrive.store(value); break;
        case P_DIST_LEVEL: mLevel.store(value); break;
        default: break;
    }
}

void Distortion::process(float* buffer, int numFrames) {
    if (!mEnabled.load()) return;
    float drive = mDrive.load();
    float level = mLevel.load();
    if (drive < 1.0f) drive = 1.0f;

    for (int i = 0; i < numFrames; ++i) {
        float x = buffer[i] * drive;
        buffer[i] = std::tanh(x) * level;
    }
}

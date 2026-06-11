#ifndef SAMPLEAPP_SAMPLESTORE_H
#define SAMPLEAPP_SAMPLESTORE_H

#include "SampleBuffer.h"
#include <array>
#include <memory>
#include <vector>

// Banque de samples + association pad -> sample.
// L'emplacement 0 est le "sample de capture" (cible de l'enregistrement micro).
class SampleStore {
public:
    static constexpr int kNumPads = 16;

    SampleStore(int captureMaxFrames) {
        // Sample de capture (id 0) toujours présent
        mSamples.push_back(std::make_shared<SampleBuffer>(captureMaxFrames));
        mPadToSample.fill(-1);
        mPadToSample[0] = 0; // pad 0 = capture par défaut
    }

    SampleBuffer* get(int id) {
        if (id < 0 || id >= (int) mSamples.size()) return nullptr;
        return mSamples[id].get();
    }

    SampleBuffer* captureSample() { return mSamples[0].get(); }
    int sampleCount() const { return (int) mSamples.size(); }

    // Ajoute un sample alloué ailleurs (import). Renvoie son id.
    int addSample(std::shared_ptr<SampleBuffer> s) {
        mSamples.push_back(std::move(s));
        return (int) mSamples.size() - 1;
    }

    void assignPad(int pad, int sampleId) {
        if (pad >= 0 && pad < kNumPads) mPadToSample[pad] = sampleId;
    }
    int padSample(int pad) const {
        if (pad < 0 || pad >= kNumPads) return -1;
        return mPadToSample[pad];
    }

private:
    std::vector<std::shared_ptr<SampleBuffer>> mSamples;
    std::array<int, kNumPads> mPadToSample{};
};

#endif // SAMPLEAPP_SAMPLESTORE_H

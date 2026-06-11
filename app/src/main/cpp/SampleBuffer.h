#ifndef SAMPLEAPP_SAMPLEBUFFER_H
#define SAMPLEAPP_SAMPLEBUFFER_H

#include <atomic>
#include <vector>
#include <mutex>

// Stocke l'échantillon mono enregistré (float PCM) et expose des opérations
// non temps réel (reverse, normalize, trim) ainsi qu'un accès lecture.
class SampleBuffer {
public:
    explicit SampleBuffer(int maxSamples);

    void clear();
    // Appelé depuis le callback d'enregistrement (temps réel). Pas d'allocation.
    void appendRealtime(const float* src, int numFrames);

    int length() const { return mLength.load(); }
    int capacity() const { return (int) mData.size(); }
    float at(int idx) const { return mData[idx]; }

    // Région de lecture
    int trimStart() const { return mTrimStart.load(); }
    int trimEnd() const { return mTrimEnd.load(); }
    void setTrim(int start, int end);
    void resetTrim();

    // Opérations destructives (quand la lecture est arrêtée)
    void reverse();
    void normalize();

    // Copie de la forme d'onde réduite pour l'affichage (downsample par pics).
    std::vector<float> getWaveform(int numPoints) const;

private:
    std::vector<float> mData;
    std::atomic<int> mLength{0};
    std::atomic<int> mTrimStart{0};
    std::atomic<int> mTrimEnd{0};
    mutable std::mutex mEditMutex;
};

#endif // SAMPLEAPP_SAMPLEBUFFER_H

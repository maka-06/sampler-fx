#ifndef SAMPLEAPP_SAMPLEBUFFER_H
#define SAMPLEAPP_SAMPLEBUFFER_H

#include <atomic>
#include <memory>
#include <vector>
#include <mutex>

// Stocke l'échantillon mono enregistré (float PCM) et expose des opérations
// non temps réel (reverse, normalize, trim) ainsi qu'un accès lecture.
class SampleBuffer {
public:
    explicit SampleBuffer(int maxSamples);

    // Crée un sample dont la capacité = la taille exacte des données fournies
    // (utilisé pour les samples chargés depuis un fichier, pour limiter la mémoire).
    static std::shared_ptr<SampleBuffer> fromData(const std::vector<float>& data);

    void clear();
    // Appelé depuis le callback d'enregistrement (temps réel). Pas d'allocation.
    void appendRealtime(const float* src, int numFrames);

    int length() const { return mLength.load(); }
    int capacity() const { return (int) mData.size(); }
    float at(int idx) const { return mData[idx]; }
    // Lecture à position fractionnaire (interpolation linéaire) pour le pitch.
    float atInterp(double pos) const;

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

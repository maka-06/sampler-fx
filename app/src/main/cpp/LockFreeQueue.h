#ifndef SAMPLEAPP_LOCKFREEQUEUE_H
#define SAMPLEAPP_LOCKFREEQUEUE_H

#include <array>
#include <atomic>

// File mono-producteur / mono-consommateur (SPSC) sans verrou.
// Producteur : thread UI (push). Consommateur : thread audio (pop).
template <typename T, int N>
class LockFreeQueue {
public:
    bool push(const T& v) {
        int t = mTail.load(std::memory_order_relaxed);
        int next = (t + 1) % N;
        if (next == mHead.load(std::memory_order_acquire)) return false; // pleine
        mBuf[t] = v;
        mTail.store(next, std::memory_order_release);
        return true;
    }

    bool pop(T& out) {
        int h = mHead.load(std::memory_order_relaxed);
        if (h == mTail.load(std::memory_order_acquire)) return false; // vide
        out = mBuf[h];
        mHead.store((h + 1) % N, std::memory_order_release);
        return true;
    }

private:
    std::array<T, N> mBuf{};
    std::atomic<int> mHead{0}; // lecture
    std::atomic<int> mTail{0}; // écriture
};

#endif // SAMPLEAPP_LOCKFREEQUEUE_H

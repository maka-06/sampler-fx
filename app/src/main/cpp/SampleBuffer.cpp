#include "SampleBuffer.h"
#include <algorithm>
#include <cmath>

SampleBuffer::SampleBuffer(int maxSamples) {
    mData.assign((size_t) maxSamples, 0.0f);
}

void SampleBuffer::clear() {
    std::lock_guard<std::mutex> lock(mEditMutex);
    mLength.store(0);
    mTrimStart.store(0);
    mTrimEnd.store(0);
}

void SampleBuffer::appendRealtime(const float* src, int numFrames) {
    int len = mLength.load();
    int cap = (int) mData.size();
    int n = std::min(numFrames, cap - len);
    if (n <= 0) return;
    for (int i = 0; i < n; ++i) {
        mData[len + i] = src[i];
    }
    int newLen = len + n;
    mLength.store(newLen);
    mTrimEnd.store(newLen);
}

float SampleBuffer::atInterp(double pos) const {
    int len = mLength.load();
    if (len <= 0) return 0.0f;
    if (pos < 0) pos = 0;
    int i0 = (int) pos;
    if (i0 >= len - 1) return mData[len - 1];
    float frac = (float) (pos - (double) i0);
    return mData[i0] * (1.0f - frac) + mData[i0 + 1] * frac;
}

void SampleBuffer::setTrim(int start, int end) {
    int len = mLength.load();
    start = std::max(0, std::min(start, len));
    end = std::max(start, std::min(end, len));
    mTrimStart.store(start);
    mTrimEnd.store(end);
}

void SampleBuffer::resetTrim() {
    mTrimStart.store(0);
    mTrimEnd.store(mLength.load());
}

void SampleBuffer::reverse() {
    std::lock_guard<std::mutex> lock(mEditMutex);
    int len = mLength.load();
    std::reverse(mData.begin(), mData.begin() + len);
}

void SampleBuffer::normalize() {
    std::lock_guard<std::mutex> lock(mEditMutex);
    int len = mLength.load();
    float peak = 0.0f;
    for (int i = 0; i < len; ++i) peak = std::max(peak, std::fabs(mData[i]));
    if (peak < 1e-6f) return;
    float gain = 0.99f / peak;
    for (int i = 0; i < len; ++i) mData[i] *= gain;
}

std::vector<float> SampleBuffer::getWaveform(int numPoints) const {
    std::vector<float> out;
    int len = mLength.load();
    if (len <= 0 || numPoints <= 0) return out;
    out.resize(numPoints, 0.0f);
    int per = std::max(1, len / numPoints);
    for (int p = 0; p < numPoints; ++p) {
        int start = p * per;
        int end = std::min(len, start + per);
        float peak = 0.0f;
        for (int i = start; i < end; ++i) peak = std::max(peak, std::fabs(mData[i]));
        out[p] = peak;
    }
    return out;
}

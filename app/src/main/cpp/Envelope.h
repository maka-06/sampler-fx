#ifndef SAMPLEAPP_ENVELOPE_H
#define SAMPLEAPP_ENVELOPE_H

#include <algorithm>

// Enveloppe d'amplitude simple (Attack / Sustain / Release) anti-clic.
class Envelope {
public:
    enum State { IDLE, ATTACK, SUSTAIN, RELEASE };

    void configure(float sampleRate, float attackMs, float releaseMs) {
        mAttackInc = 1.0f / std::max(1.0f, sampleRate * attackMs * 0.001f);
        mReleaseInc = 1.0f / std::max(1.0f, sampleRate * releaseMs * 0.001f);
    }

    void noteOn() { mState = ATTACK; mLevel = 0.0f; }
    void noteOff() { if (mState != IDLE) mState = RELEASE; }
    bool isActive() const { return mState != IDLE; }

    float next() {
        switch (mState) {
            case ATTACK:
                mLevel += mAttackInc;
                if (mLevel >= 1.0f) { mLevel = 1.0f; mState = SUSTAIN; }
                break;
            case RELEASE:
                mLevel -= mReleaseInc;
                if (mLevel <= 0.0f) { mLevel = 0.0f; mState = IDLE; }
                break;
            case SUSTAIN:
            case IDLE:
                break;
        }
        return mLevel;
    }

private:
    State mState = IDLE;
    float mLevel = 0.0f;
    float mAttackInc = 0.01f;
    float mReleaseInc = 0.001f;
};

#endif // SAMPLEAPP_ENVELOPE_H

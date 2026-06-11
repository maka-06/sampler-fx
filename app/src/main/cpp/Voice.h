#ifndef SAMPLEAPP_VOICE_H
#define SAMPLEAPP_VOICE_H

#include "SampleBuffer.h"
#include "Envelope.h"
#include <cstdint>

// Mode de déclenchement d'une voix.
enum TriggerMode {
    TRIG_ONESHOT = 0, // joue jusqu'à la fin
    TRIG_GATE = 1,    // joue tant que maintenu (NOTE_OFF -> release)
    TRIG_LOOP = 2     // boucle sur la région de trim
};

// Événement de note transmis de l'UI vers le thread audio (via file lock-free).
struct NoteEvent {
    enum Type { NOTE_ON, NOTE_OFF } type = NOTE_ON;
    int padId = 0;
    int sampleId = 0;
    float pitchRatio = 1.0f;
    float gain = 1.0f;
    int triggerMode = TRIG_ONESHOT;
    bool useGlobalLoop = false; // le bouton "play" suit le flag loop global
};

// Une voix de lecture polyphonique.
struct Voice {
    bool active = false;
    SampleBuffer* sample = nullptr;
    double readPos = 0.0;
    double pitchRatio = 1.0;
    float gain = 1.0f;
    int triggerMode = TRIG_ONESHOT;
    bool useGlobalLoop = false;
    int padId = -1;
    uint64_t order = 0; // pour le "voice stealing" (plus ancien volé en premier)
    Envelope env;
};

#endif // SAMPLEAPP_VOICE_H

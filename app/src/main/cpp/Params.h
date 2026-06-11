#ifndef SAMPLEAPP_PARAMS_H
#define SAMPLEAPP_PARAMS_H

// Identifiants d'effets (ordre fixe dans la chaîne de traitement)
enum EffectId {
    FX_FILTER = 0,
    FX_DISTORTION = 1,
    FX_PITCH = 2,
    FX_DELAY = 3,
    FX_REVERB = 4,
    FX_COUNT = 5
};

// Identifiants de paramètres (génériques, interprétés par chaque effet)
enum ParamId {
    // Filtre
    P_FILTER_TYPE = 0,   // 0 = passe-bas, 1 = passe-haut
    P_FILTER_CUTOFF = 1, // Hz
    P_FILTER_Q = 2,
    // Distortion
    P_DIST_DRIVE = 10,
    P_DIST_LEVEL = 11,
    // Pitch
    P_PITCH_SEMITONES = 20,
    // Delay
    P_DELAY_TIME = 30,     // ms
    P_DELAY_FEEDBACK = 31, // 0..1
    P_DELAY_MIX = 32,      // 0..1
    // Reverb
    P_REVERB_ROOMSIZE = 40, // 0..1
    P_REVERB_DAMPING = 41,  // 0..1
    P_REVERB_MIX = 42       // 0..1
};

#endif // SAMPLEAPP_PARAMS_H

# Sampler FX

Application Android pour **échantillonner un son avec le micro** du smartphone et lui appliquer une **chaîne d'effets temps réel**.

Inspirée de projets open source comme *Loom Groovebox*, *Amp Rack* et *MicUp*, elle est bâtie sur **Oboe** (moteur audio C++ basse latence de Google) avec une UI **Jetpack Compose**.

## Fonctionnalités

- 🎙️ **Enregistrement** depuis le micro (faible latence via Oboe / AAudio).
- ▶️ **Lecture** avec boucle, tête de lecture animée.
- 🌊 **Forme d'onde** interactive avec **trim** (rognage début/fin par glisser).
- 🎛️ **Chaîne d'effets** (ordre fixe) :
  1. **Filtre** passe-bas / passe-haut (biquad)
  2. **Distortion** (waveshaping `tanh`)
  3. **Pitch shift** temps réel (delay-line à 2 taps, durée préservée)
  4. **Delay** (ligne à retard + feedback + mix)
  5. **Reverb** (Freeverb : 8 combs + 4 allpass)
- 🔁 **Reverse** et **Normalisation** du sample.
- 💾 **Presets** (sauvegarde / chargement JSON).
- 📤 **Export WAV** 16-bit du rendu (sample + effets).

## Architecture

```
app/src/main/
├── cpp/                      # Moteur natif (C++17 + Oboe)
│   ├── AudioEngine.*         # Flux Oboe entrée/sortie + callbacks temps réel
│   ├── SampleBuffer.*        # Stockage PCM + reverse/normalize/trim/waveform
│   ├── native-lib.cpp        # Fonctions JNI
│   └── effects/              # Biquad, Distortion, PitchShifter, Delay, Reverb, EffectChain
└── java/com/example/sampleapp/
    ├── NativeBridge.kt       # Déclarations external (JNI)
    ├── audio/                # AudioController (ViewModel), Effects (catalogue), Preset
    ├── ui/                   # SamplerScreen, WaveformView, EffectPedal, theme
    └── MainActivity.kt       # Permission micro + câblage Compose
```

Le DSP s'exécute dans le callback haute priorité d'Oboe (`onAudioReady`). Les paramètres sont passés depuis Kotlin via JNI et stockés en `std::atomic` pour être lus sans verrou côté audio.

## Prérequis

- **Android Studio** (Ladybug ou plus récent)
- **NDK** 26+ et **CMake** 3.22.1 (installables via le SDK Manager)
- **JDK 17**
- Appareil Android **API 26+** (Android 8.0). Un appareil **physique** est recommandé (le micro et la faible latence ne sont pas fiables sur émulateur).

## Build

1. Ouvrir le dossier dans Android Studio (il génère `local.properties` avec le chemin du SDK et le wrapper Gradle au besoin).
2. Laisser Gradle synchroniser : la dépendance `com.google.oboe:oboe` est récupérée via **Prefab**.
3. Brancher un appareil, puis **Run** ▶.

En ligne de commande (après avoir généré le wrapper avec `gradle wrapper`) :

```bash
./gradlew :app:assembleDebug
```

## Notes & limites

- ⚠️ **Larsen** : la lecture passe par le haut-parleur ; il n'y a pas de monitoring live du micro pour éviter le feedback. Utilise un casque si tu ajoutes le monitoring.
- Sample mono, 60 s max, à la fréquence native de l'appareil (souvent 48 kHz).
- L'ordre des effets est fixe (Filtre → Distortion → Pitch → Delay → Reverb). Le réordonnancement type pedalboard est une évolution possible.
- Le pitch shifter temps réel (domaine temporel) peut introduire un léger artefact ; un phase vocoder FFT donnerait une meilleure qualité.

## Pistes d'évolution

- Réordonnancement des effets (drag & drop).
- Pitch shift par phase vocoder (FFT).
- Multi-échantillons / pads déclenchables.
- Enregistrement stéréo + monitoring casque.

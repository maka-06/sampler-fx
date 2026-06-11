package com.example.sampleapp.audio

/** Identifiants d'effets — doivent rester synchronisés avec Params.h (C++). */
object EffectId {
    const val FILTER = 0
    const val DISTORTION = 1
    const val PITCH = 2
    const val DELAY = 3
    const val REVERB = 4
}

/** Identifiants de paramètres — synchronisés avec Params.h (C++). */
object ParamId {
    const val FILTER_TYPE = 0
    const val FILTER_CUTOFF = 1
    const val FILTER_Q = 2
    const val DIST_DRIVE = 10
    const val DIST_LEVEL = 11
    const val PITCH_SEMITONES = 20
    const val DELAY_TIME = 30
    const val DELAY_FEEDBACK = 31
    const val DELAY_MIX = 32
    const val REVERB_ROOMSIZE = 40
    const val REVERB_DAMPING = 41
    const val REVERB_MIX = 42
}

/** Description d'un paramètre réglable d'un effet (pour l'UI). */
data class ParamSpec(
    val paramId: Int,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val unit: String = "",
    val stepInt: Boolean = false
)

/** Description d'un effet et de ses paramètres. */
data class EffectSpec(
    val effectId: Int,
    val name: String,
    val params: List<ParamSpec>
)

/** Catalogue statique des effets disponibles, dans l'ordre de la chaîne. */
val EFFECT_CATALOG: List<EffectSpec> = listOf(
    EffectSpec(
        EffectId.FILTER, "Filtre",
        listOf(
            ParamSpec(ParamId.FILTER_TYPE, "Type (0=LP / 1=HP)", 0f, 1f, 0f, stepInt = true),
            ParamSpec(ParamId.FILTER_CUTOFF, "Fréquence", 50f, 18000f, 2000f, "Hz"),
            ParamSpec(ParamId.FILTER_Q, "Résonance", 0.2f, 12f, 0.707f)
        )
    ),
    EffectSpec(
        EffectId.DISTORTION, "Distortion",
        listOf(
            ParamSpec(ParamId.DIST_DRIVE, "Drive", 1f, 40f, 6f),
            ParamSpec(ParamId.DIST_LEVEL, "Niveau", 0f, 1f, 0.6f)
        )
    ),
    EffectSpec(
        EffectId.PITCH, "Pitch",
        listOf(
            ParamSpec(ParamId.PITCH_SEMITONES, "Demi-tons", -12f, 12f, 0f, "st")
        )
    ),
    EffectSpec(
        EffectId.DELAY, "Delay",
        listOf(
            ParamSpec(ParamId.DELAY_TIME, "Temps", 20f, 1500f, 300f, "ms"),
            ParamSpec(ParamId.DELAY_FEEDBACK, "Feedback", 0f, 0.95f, 0.4f),
            ParamSpec(ParamId.DELAY_MIX, "Mix", 0f, 1f, 0.35f)
        )
    ),
    EffectSpec(
        EffectId.REVERB, "Reverb",
        listOf(
            ParamSpec(ParamId.REVERB_ROOMSIZE, "Taille", 0f, 1f, 0.7f),
            ParamSpec(ParamId.REVERB_DAMPING, "Amortissement", 0f, 1f, 0.5f),
            ParamSpec(ParamId.REVERB_MIX, "Mix", 0f, 1f, 0.3f)
        )
    )
)

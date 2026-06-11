package com.example.sampleapp.audio

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ParamValue(val paramId: Int, val value: Float)

@Serializable
data class FxSnapshot(val effectId: Int, val enabled: Boolean, val params: List<ParamValue>)

@Serializable
data class Preset(val name: String, val effects: List<FxSnapshot>)

/** Stockage simple des presets dans un fichier JSON de l'app. */
class PresetManager(context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "presets.json")

    fun load(): List<Preset> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<Preset>>(file.readText()) }
            .getOrDefault(emptyList())
    }

    fun saveAll(presets: List<Preset>) {
        runCatching { file.writeText(json.encodeToString(presets)) }
    }

    fun upsert(preset: Preset): List<Preset> {
        val list = load().filterNot { it.name == preset.name } + preset
        saveAll(list)
        return list
    }

    fun delete(name: String): List<Preset> {
        val list = load().filterNot { it.name == name }
        saveAll(list)
        return list
    }
}

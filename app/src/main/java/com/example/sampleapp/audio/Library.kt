package com.example.sampleapp.audio

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class LibrarySample(
    val id: String,
    val name: String,
    val fileName: String,
    val category: String,
    val durationSec: Float
)

@Serializable
data class Library(
    val categories: List<String> = listOf("Défaut"),
    val samples: List<LibrarySample> = emptyList()
)

/**
 * Gère la bibliothèque de samples : un dossier de fichiers WAV + un index JSON.
 * Les catégories sont créées librement par l'utilisateur.
 */
class LibraryManager(context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "library").apply { mkdirs() }
    private val index = File(dir, "library.json")

    fun fileFor(sample: LibrarySample): File = File(dir, sample.fileName)

    fun load(): Library {
        if (!index.exists()) return Library()
        return runCatching { json.decodeFromString<Library>(index.readText()) }
            .getOrDefault(Library())
    }

    fun save(library: Library) {
        runCatching { index.writeText(json.encodeToString(library)) }
    }

    fun addCategory(name: String): Library {
        val lib = load()
        if (lib.categories.any { it.equals(name, ignoreCase = true) }) return lib
        val updated = lib.copy(categories = lib.categories + name)
        save(updated)
        return updated
    }

    fun renameCategory(oldName: String, newName: String): Library {
        val lib = load()
        val cats = lib.categories.map { if (it == oldName) newName else it }
        val samples = lib.samples.map { if (it.category == oldName) it.copy(category = newName) else it }
        val updated = lib.copy(categories = cats, samples = samples)
        save(updated)
        return updated
    }

    fun deleteCategory(name: String): Library {
        val lib = load()
        // Supprime la catégorie ; les samples qui y étaient sont supprimés (fichiers inclus)
        lib.samples.filter { it.category == name }.forEach { runCatching { fileFor(it).delete() } }
        val updated = lib.copy(
            categories = lib.categories.filterNot { it == name },
            samples = lib.samples.filterNot { it.category == name }
        )
        save(updated)
        return updated
    }

    /**
     * Réserve une entrée de sample et renvoie le fichier WAV cible à remplir.
     * L'appelant écrit le WAV puis appelle [commitSample].
     */
    fun newSampleFile(): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.wav"
        return id to File(dir, fileName)
    }

    fun commitSample(id: String, name: String, category: String, durationSec: Float): Library {
        val lib = load()
        val cats = if (lib.categories.contains(category)) lib.categories else lib.categories + category
        val sample = LibrarySample(id, name, "$id.wav", category, durationSec)
        val updated = lib.copy(categories = cats, samples = lib.samples + sample)
        save(updated)
        return updated
    }

    fun deleteSample(id: String): Library {
        val lib = load()
        lib.samples.firstOrNull { it.id == id }?.let { runCatching { fileFor(it).delete() } }
        val updated = lib.copy(samples = lib.samples.filterNot { it.id == id })
        save(updated)
        return updated
    }

    fun moveSample(id: String, category: String): Library {
        val lib = load()
        val updated = lib.copy(
            samples = lib.samples.map { if (it.id == id) it.copy(category = category) else it }
        )
        save(updated)
        return updated
    }

    fun renameSample(id: String, name: String): Library {
        val lib = load()
        val updated = lib.copy(
            samples = lib.samples.map { if (it.id == id) it.copy(name = name) else it }
        )
        save(updated)
        return updated
    }
}

package com.example.sampleapp.audio

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sampleapp.NativeBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FxState(val enabled: Boolean, val params: Map<Int, Float>)

object Trigger {
    const val ONESHOT = 0
    const val GATE = 1
    const val LOOP = 2
}

const val NUM_PADS = 16

data class PadState(
    val index: Int,
    val librarySampleId: String? = null,
    val name: String? = null,
    val triggerMode: Int = Trigger.ONESHOT
) {
    val hasSample: Boolean get() = librarySampleId != null
}

data class UiState(
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val loop: Boolean = false,
    val hasSample: Boolean = false,
    val sampleRate: Int = 48000,
    val durationSec: Float = 0f,
    val waveform: List<Float> = emptyList(),
    val playHeadFraction: Float = 0f,
    val trimStart: Float = 0f,
    val trimEnd: Float = 1f,
    val effects: Map<Int, FxState> = emptyMap(),
    val presetNames: List<String> = emptyList(),
    val library: Library = Library(),
    val pads: List<PadState> = (0 until NUM_PADS).map { PadState(it) },
    val selectedPad: Int = 0,
    val pendingSaveDuration: Float? = null, // != null -> proposer la sauvegarde de l'enregistrement
    val message: String? = null
)

class AudioController(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val presetManager = PresetManager(app)
    private val libraryManager = LibraryManager(app)
    private var pollJob: Job? = null

    // Cache : id de sample de bibliothèque -> id natif chargé dans le moteur
    private val loadedSampleIds = mutableMapOf<String, Int>()

    init {
        NativeBridge.nativeInit()
        // Pousse les valeurs par défaut vers le moteur natif
        EFFECT_CATALOG.forEach { spec ->
            spec.params.forEach { p ->
                NativeBridge.setEffectParam(spec.effectId, p.paramId, p.default)
            }
            NativeBridge.setEffectEnabled(spec.effectId, false)
        }
        _state.update {
            it.copy(
                sampleRate = NativeBridge.getSampleRate(),
                presetNames = presetManager.load().map { p -> p.name },
                library = libraryManager.load()
            )
        }
    }

    private fun initialState(): UiState {
        val effects = EFFECT_CATALOG.associate { spec ->
            spec.effectId to FxState(
                enabled = false,
                params = spec.params.associate { it.paramId to it.default }
            )
        }
        return UiState(effects = effects)
    }

    // --- Enregistrement / lecture -------------------------------------------------

    fun toggleRecord() {
        if (_state.value.isRecording) {
            NativeBridge.stopRecording()
            refreshSampleInfo()
            val dur = _state.value.durationSec
            _state.update {
                it.copy(
                    isRecording = false,
                    pendingSaveDuration = if (dur > 0f) dur else null,
                    message = "Sample enregistré"
                )
            }
        } else {
            stopPolling()
            if (NativeBridge.startRecording()) {
                _state.update { it.copy(isRecording = true, isPlaying = false, message = "Enregistrement…") }
            } else {
                _state.update { it.copy(message = "Échec de l'enregistrement (micro indisponible ?)") }
            }
        }
    }

    fun togglePlay() {
        if (_state.value.isPlaying) {
            NativeBridge.stopPlayback()
            stopPolling()
            _state.update { it.copy(isPlaying = false) }
        } else {
            if (NativeBridge.startPlayback()) {
                _state.update { it.copy(isPlaying = true) }
                startPolling()
            } else {
                _state.update { it.copy(message = "Aucun sample à lire") }
            }
        }
    }

    fun setLoop(loop: Boolean) {
        NativeBridge.setLoop(loop)
        _state.update { it.copy(loop = loop) }
    }

    // --- Édition du sample --------------------------------------------------------

    fun reverse() {
        NativeBridge.reverseSample()
        refreshSampleInfo()
        _state.update { it.copy(message = "Sample inversé") }
    }

    fun normalize() {
        NativeBridge.normalizeSample()
        refreshSampleInfo()
        _state.update { it.copy(message = "Sample normalisé") }
    }

    fun setTrim(start: Float, end: Float) {
        val len = NativeBridge.getSampleLength()
        if (len <= 0) return
        val s = (start.coerceIn(0f, 1f) * len).toInt()
        val e = (end.coerceIn(0f, 1f) * len).toInt()
        NativeBridge.setTrim(s, e)
        _state.update { it.copy(trimStart = start, trimEnd = end) }
    }

    // --- Effets -------------------------------------------------------------------

    fun setEffectEnabled(effectId: Int, enabled: Boolean) {
        NativeBridge.setEffectEnabled(effectId, enabled)
        _state.update { st ->
            val fx = st.effects[effectId] ?: return@update st
            st.copy(effects = st.effects + (effectId to fx.copy(enabled = enabled)))
        }
    }

    fun setEffectParam(effectId: Int, paramId: Int, value: Float) {
        NativeBridge.setEffectParam(effectId, paramId, value)
        _state.update { st ->
            val fx = st.effects[effectId] ?: return@update st
            st.copy(effects = st.effects + (effectId to fx.copy(params = fx.params + (paramId to value))))
        }
    }

    // --- Presets ------------------------------------------------------------------

    fun savePreset(name: String) {
        val snapshots = _state.value.effects.map { (id, fx) ->
            FxSnapshot(id, fx.enabled, fx.params.map { ParamValue(it.key, it.value) })
        }
        val names = presetManager.upsert(Preset(name, snapshots)).map { it.name }
        _state.update { it.copy(presetNames = names, message = "Preset « $name » enregistré") }
    }

    fun loadPreset(name: String) {
        val preset = presetManager.load().firstOrNull { it.name == name } ?: return
        preset.effects.forEach { snap ->
            snap.params.forEach { pv -> NativeBridge.setEffectParam(snap.effectId, pv.paramId, pv.value) }
            NativeBridge.setEffectEnabled(snap.effectId, snap.enabled)
        }
        _state.update { st ->
            val newEffects = st.effects.toMutableMap()
            preset.effects.forEach { snap ->
                newEffects[snap.effectId] = FxState(snap.enabled, snap.params.associate { it.paramId to it.value })
            }
            st.copy(effects = newEffects, message = "Preset « $name » chargé")
        }
    }

    fun deletePreset(name: String) {
        val names = presetManager.delete(name).map { it.name }
        _state.update { it.copy(presetNames = names) }
    }

    // --- Export -------------------------------------------------------------------

    /** Nom de fichier suggéré pour le sélecteur système. */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "sample_$stamp.wav"
    }

    fun hasSample(): Boolean = _state.value.hasSample

    /** Écrit le rendu (sample + effets) dans le descripteur fourni par le sélecteur SAF. */
    fun exportToFd(fd: Int) {
        if (!_state.value.hasSample) {
            _state.update { it.copy(message = "Rien à exporter") }
            return
        }
        val ok = NativeBridge.exportWavToFd(fd)
        _state.update {
            it.copy(message = if (ok) "Fichier WAV exporté" else "Échec de l'export")
        }
    }

    // --- Bibliothèque -------------------------------------------------------------

    /** Annule la proposition de sauvegarde après enregistrement. */
    fun dismissSaveDialog() = _state.update { it.copy(pendingSaveDuration = null) }

    /** Sauve la capture courante (id natif 0) dans la bibliothèque. */
    fun saveRecordingToLibrary(name: String, category: String) {
        val duration = _state.value.pendingSaveDuration ?: _state.value.durationSec
        val (id, file) = libraryManager.newSampleFile()
        val ok = runCatching {
            val pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_TRUNCATE
            )
            NativeBridge.saveCaptureToFd(pfd.detachFd())
        }.getOrDefault(false)

        if (!ok) {
            file.delete()
            _state.update { it.copy(pendingSaveDuration = null, message = "Échec de la sauvegarde") }
            return
        }
        val finalName = name.ifBlank { "Sample" }
        val finalCat = category.ifBlank { "Défaut" }
        val lib = libraryManager.commitSample(id, finalName, finalCat, duration)
        _state.update {
            it.copy(library = lib, pendingSaveDuration = null, message = "Ajouté à la bibliothèque")
        }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        _state.update { it.copy(library = libraryManager.addCategory(name.trim())) }
    }

    fun renameCategory(oldName: String, newName: String) {
        if (newName.isBlank()) return
        _state.update { it.copy(library = libraryManager.renameCategory(oldName, newName.trim())) }
    }

    fun deleteCategory(name: String) {
        _state.update { it.copy(library = libraryManager.deleteCategory(name)) }
    }

    fun deleteSample(id: String) {
        val lib = libraryManager.deleteSample(id)
        loadedSampleIds.remove(id)
        // Retire ce sample des pads qui le référençaient
        val pads = _state.value.pads.map {
            if (it.librarySampleId == id) {
                NativeBridge.assignPadSample(it.index, -1)
                it.copy(librarySampleId = null, name = null)
            } else it
        }
        _state.update { it.copy(library = lib, pads = pads) }
    }

    fun moveSample(id: String, category: String) {
        _state.update { it.copy(library = libraryManager.moveSample(id, category)) }
    }

    fun renameSample(id: String, name: String) {
        if (name.isBlank()) return
        val lib = libraryManager.renameSample(id, name.trim())
        val pads = _state.value.pads.map {
            if (it.librarySampleId == id) it.copy(name = name.trim()) else it
        }
        _state.update { it.copy(library = lib, pads = pads) }
    }

    /** Joue un sample de la bibliothèque en aperçu (le charge si besoin, sans toucher aux pads). */
    fun previewSample(librarySampleId: String) {
        val nativeId = ensureLoaded(librarySampleId) ?: run {
            _state.update { it.copy(message = "Sample introuvable") }
            return
        }
        NativeBridge.setSelectedSample(nativeId)
        NativeBridge.startPlayback()
        _state.update { it.copy(isPlaying = true) }
        startPolling()
    }

    // --- Pads ---------------------------------------------------------------------

    fun assignToPad(pad: Int, librarySampleId: String) {
        val nativeId = ensureLoaded(librarySampleId) ?: run {
            _state.update { it.copy(message = "Échec du chargement du sample") }
            return
        }
        NativeBridge.assignPadSample(pad, nativeId)
        val sample = _state.value.library.samples.firstOrNull { it.id == librarySampleId }
        val pads = _state.value.pads.map {
            if (it.index == pad) it.copy(librarySampleId = librarySampleId, name = sample?.name) else it
        }
        _state.update { it.copy(pads = pads, message = "Assigné au pad ${pad + 1}") }
    }

    fun clearPad(pad: Int) {
        NativeBridge.assignPadSample(pad, -1)
        val pads = _state.value.pads.map {
            if (it.index == pad) it.copy(librarySampleId = null, name = null) else it
        }
        _state.update { it.copy(pads = pads) }
    }

    fun setPadMode(pad: Int, mode: Int) {
        val pads = _state.value.pads.map {
            if (it.index == pad) it.copy(triggerMode = mode) else it
        }
        _state.update { it.copy(pads = pads) }
    }

    /** Déclenche un pad (NOTE_ON) et le sélectionne pour l'édition. */
    fun playPad(pad: Int) {
        val padState = _state.value.pads.getOrNull(pad) ?: return
        selectPad(pad)
        if (!padState.hasSample) return
        NativeBridge.triggerPad(pad, 1f, 1f, padState.triggerMode)
        _state.update { it.copy(isPlaying = true) }
        startPolling()
    }

    /** Relâche un pad (NOTE_OFF) - utile en mode gate. */
    fun releasePad(pad: Int) {
        NativeBridge.releasePad(pad)
    }

    /** Sélectionne un pad : l'édition (waveform/trim/effets/export) cible ce sample. */
    fun selectPad(pad: Int) {
        val padState = _state.value.pads.getOrNull(pad) ?: return
        _state.update { it.copy(selectedPad = pad) }
        val libId = padState.librarySampleId
        if (libId != null) {
            val nativeId = loadedSampleIds[libId]
            if (nativeId != null) {
                NativeBridge.setSelectedSample(nativeId)
                refreshSampleInfo()
            }
        } else {
            _state.update {
                it.copy(hasSample = false, durationSec = 0f, waveform = emptyList(), playHeadFraction = 0f)
            }
        }
    }

    /** Charge le WAV du sample dans le moteur si nécessaire ; renvoie l'id natif. */
    private fun ensureLoaded(librarySampleId: String): Int? {
        loadedSampleIds[librarySampleId]?.let { return it }
        val sample = _state.value.library.samples.firstOrNull { it.id == librarySampleId } ?: return null
        val file = libraryManager.fileFor(sample)
        if (!file.exists()) return null
        val nativeId = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            NativeBridge.loadWavFromFd(pfd.detachFd())
        }.getOrDefault(-1)
        if (nativeId < 0) return null
        loadedSampleIds[librarySampleId] = nativeId
        return nativeId
    }

    // --- Divers -------------------------------------------------------------------

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun refreshSampleInfo() {
        val len = NativeBridge.getSampleLength()
        val rate = NativeBridge.getSampleRate().coerceAtLeast(1)
        val wf = NativeBridge.getWaveform(400).toList()
        NativeBridge.resetTrim()
        _state.update {
            it.copy(
                hasSample = len > 0,
                durationSec = len.toFloat() / rate,
                waveform = wf,
                trimStart = 0f,
                trimEnd = 1f,
                playHeadFraction = 0f
            )
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (NativeBridge.isPlaying()) {
                val len = NativeBridge.getSampleLength().coerceAtLeast(1)
                val frac = NativeBridge.getPlayHead().toFloat() / len
                _state.update { it.copy(playHeadFraction = frac.coerceIn(0f, 1f)) }
                delay(33)
            }
            // Lecture terminée d'elle-même : on ferme proprement le flux de sortie
            NativeBridge.stopPlayback()
            _state.update { it.copy(isPlaying = false, playHeadFraction = 0f) }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        NativeBridge.stopPlayback()
        NativeBridge.stopRecording()
        NativeBridge.nativeDestroy()
        super.onCleared()
    }
}

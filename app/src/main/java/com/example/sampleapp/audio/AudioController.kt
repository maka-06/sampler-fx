package com.example.sampleapp.audio

import android.app.Application
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
    val message: String? = null
)

class AudioController(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val presetManager = PresetManager(app)
    private var pollJob: Job? = null

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
                presetNames = presetManager.load().map { p -> p.name }
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
            _state.update { it.copy(isRecording = false, message = "Sample enregistré") }
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

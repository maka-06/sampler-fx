package com.example.sampleapp

/**
 * Pont JNI vers le moteur audio natif (Oboe + DSP C++).
 * Les identifiants d'effets/paramètres correspondent à ceux de Params.h.
 */
object NativeBridge {

    init {
        System.loadLibrary("sampleapp")
    }

    external fun nativeInit()
    external fun nativeDestroy()

    external fun startRecording(): Boolean
    external fun stopRecording()
    external fun startPlayback(): Boolean
    external fun stopPlayback()

    external fun setLoop(loop: Boolean)
    external fun isRecording(): Boolean
    external fun isPlaying(): Boolean

    external fun clearSample()
    external fun reverseSample()
    external fun normalizeSample()
    external fun setTrim(start: Int, end: Int)
    external fun resetTrim()

    external fun setEffectEnabled(effectId: Int, enabled: Boolean)
    external fun setEffectParam(effectId: Int, paramId: Int, value: Float)

    external fun getSampleLength(): Int
    external fun getSampleRate(): Int
    external fun getPlayHead(): Int
    external fun getWaveform(numPoints: Int): FloatArray

    external fun exportWav(path: String): Boolean
    external fun exportWavToFd(fd: Int): Boolean
}

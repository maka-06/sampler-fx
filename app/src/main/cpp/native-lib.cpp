#include <jni.h>
#include <memory>
#include <string>
#include "AudioEngine.h"

static std::unique_ptr<AudioEngine> gEngine;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_nativeInit(JNIEnv*, jobject) {
    if (!gEngine) gEngine = std::make_unique<AudioEngine>();
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_nativeDestroy(JNIEnv*, jobject) {
    gEngine.reset();
}

JNIEXPORT jboolean JNICALL
Java_com_example_sampleapp_NativeBridge_startRecording(JNIEnv*, jobject) {
    return gEngine && gEngine->startRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_stopRecording(JNIEnv*, jobject) {
    if (gEngine) gEngine->stopRecording();
}

JNIEXPORT jboolean JNICALL
Java_com_example_sampleapp_NativeBridge_startPlayback(JNIEnv*, jobject) {
    return gEngine && gEngine->startPlayback() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_stopPlayback(JNIEnv*, jobject) {
    if (gEngine) gEngine->stopPlayback();
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_setLoop(JNIEnv*, jobject, jboolean loop) {
    if (gEngine) gEngine->setLoop(loop == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_triggerPad(JNIEnv*, jobject, jint padId,
                                                   jfloat pitchRatio, jfloat gain, jint triggerMode) {
    if (gEngine) gEngine->triggerPad(padId, pitchRatio, gain, triggerMode);
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_releasePad(JNIEnv*, jobject, jint padId) {
    if (gEngine) gEngine->releasePad(padId);
}

JNIEXPORT jint JNICALL
Java_com_example_sampleapp_NativeBridge_getSampleCount(JNIEnv*, jobject) {
    return gEngine ? gEngine->getSampleCount() : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_example_sampleapp_NativeBridge_isRecording(JNIEnv*, jobject) {
    return gEngine && gEngine->isRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_sampleapp_NativeBridge_isPlaying(JNIEnv*, jobject) {
    return gEngine && gEngine->isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_clearSample(JNIEnv*, jobject) {
    if (gEngine) gEngine->clearSample();
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_reverseSample(JNIEnv*, jobject) {
    if (gEngine) gEngine->reverseSample();
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_normalizeSample(JNIEnv*, jobject) {
    if (gEngine) gEngine->normalizeSample();
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_setTrim(JNIEnv*, jobject, jint start, jint end) {
    if (gEngine) gEngine->setTrim(start, end);
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_resetTrim(JNIEnv*, jobject) {
    if (gEngine) gEngine->resetTrim();
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_setEffectEnabled(JNIEnv*, jobject, jint effectId, jboolean enabled) {
    if (gEngine) gEngine->setEffectEnabled(effectId, enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_sampleapp_NativeBridge_setEffectParam(JNIEnv*, jobject, jint effectId, jint paramId, jfloat value) {
    if (gEngine) gEngine->setEffectParam(effectId, paramId, value);
}

JNIEXPORT jint JNICALL
Java_com_example_sampleapp_NativeBridge_getSampleLength(JNIEnv*, jobject) {
    return gEngine ? gEngine->getSampleLength() : 0;
}

JNIEXPORT jint JNICALL
Java_com_example_sampleapp_NativeBridge_getSampleRate(JNIEnv*, jobject) {
    return gEngine ? gEngine->getSampleRate() : 0;
}

JNIEXPORT jint JNICALL
Java_com_example_sampleapp_NativeBridge_getPlayHead(JNIEnv*, jobject) {
    return gEngine ? gEngine->getPlayHead() : 0;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_sampleapp_NativeBridge_getWaveform(JNIEnv* env, jobject, jint numPoints) {
    std::vector<float> wf;
    if (gEngine) wf = gEngine->getWaveform(numPoints);
    jfloatArray arr = env->NewFloatArray((jsize) wf.size());
    if (arr && !wf.empty()) {
        env->SetFloatArrayRegion(arr, 0, (jsize) wf.size(), wf.data());
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_example_sampleapp_NativeBridge_exportWav(JNIEnv* env, jobject, jstring path) {
    if (!gEngine) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool ok = gEngine->exportWav(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_sampleapp_NativeBridge_exportWavToFd(JNIEnv*, jobject, jint fd) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->exportWavFd((int) fd) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

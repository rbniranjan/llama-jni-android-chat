#include <jni.h>
#include <android/log.h>

#define TAG "llama-interactive"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_sx_llama_jni_MainActivity_createLLModel(JNIEnv *, jobject, jstring, jint) {
    LOGI("interactive JNI is not enabled in this build");
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sx_llama_jni_MainActivity_initLLModel(JNIEnv *, jobject, jlong, jstring, jstring) {
    LOGI("interactive JNI is not enabled in this build");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sx_llama_jni_MainActivity_whileLLModel(JNIEnv *, jobject, jlong) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sx_llama_jni_MainActivity_breakLLModel(JNIEnv *, jobject, jlong) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sx_llama_jni_MainActivity_printLLModel(JNIEnv *, jobject, jlong) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_sx_llama_jni_MainActivity_embdLLModel(JNIEnv *env, jobject, jlong) {
    return env->NewIntArray(0);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sx_llama_jni_MainActivity_textLLModel(JNIEnv *env, jobject, jlong, jint) {
    return env->NewByteArray(0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sx_llama_jni_MainActivity_releaseLLModel(JNIEnv *, jobject, jlong) {
    LOGI("interactive JNI is not enabled in this build");
}

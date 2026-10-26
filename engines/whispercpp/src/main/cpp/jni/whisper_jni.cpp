// JNI bridge between the Kotlin engine and the vendored whisper.cpp runtime.
//
// The contract exposed to Kotlin is intentionally tiny: load a GGUF model,
// run whisper_full over caller-supplied float samples, read back segments,
// free the context. Cancellation is a pointer to a std::atomic<bool> handed to
// whisper's abort_callback so a running transcription can be interrupted
// without unloading the context on another thread.
#include <jni.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "whisper.h"
#include <android/log.h>

#define TW_TAG "WhisperCppJni"
#define TW_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TW_TAG, __VA_ARGS__)

// Segment timestamps from whisper are in 10 ms units; the engine API speaks ms.
static constexpr int64_t kWhisperTimeUnitMs = 10;

// The context params force the CPU path even if a future ggml build links a GPU
// backend — on Android we never register one, but relying on "absent" would be
// implicit; asking for CPU keeps the runtime behavior explicit.
static bool abort_if_requested(void * user_data) {
    auto * flag = static_cast<std::atomic<bool> *>(user_data);
    return flag != nullptr && flag->load(std::memory_order_relaxed);
}

struct SegmentSink {
    std::string buffer;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeInit(
        JNIEnv * env, jobject /*thiz*/, jstring model_path) {
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        return 0;
    }

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    struct whisper_context * ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        TW_LOGE("failed to load model");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeRelease(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctx_ptr) {
    if (ctx_ptr == 0) {
        return;
    }
    auto * ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    // whisper_free is not thread-safe against an in-flight whisper_full on the
    // same context; Kotlin guarantees release happens only after transcribe
    // has returned or been aborted.
    whisper_free(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeCancelNew(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    // Heap-owned flag so the JNI thread can flip "cancel" while whisper_full
    // runs and polls it from its abort_callback.
    auto * flag = new std::atomic<bool>(false);
    return reinterpret_cast<jlong>(flag);
}

JNIEXPORT void JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeCancelRequest(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) {
        return;
    }
    auto * flag = reinterpret_cast<std::atomic<bool> *>(handle);
    flag->store(true, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeCancelFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) {
        return;
    }
    delete reinterpret_cast<std::atomic<bool> *>(handle);
}

// Runs full inference. Returns a serialized result:
//   lang=<whisper language name>\n<count>\n then one line per segment:
//   <startMs>\t<endMs>\t<text>
// A leading "~" on the lang line marks an aborted run. Returning a string keeps
// the marshalling trivial; the Kotlin side parses it into TranscriptSegment.
JNIEXPORT jstring JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeTranscribe(
        JNIEnv * env, jobject /*thiz*/, jlong ctx_ptr, jfloatArray samples_arr,
        jint n_threads, jstring language, jlong cancel_handle) {
    if (ctx_ptr == 0) {
        return env->NewStringUTF("");
    }
    auto * ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);

    jsize len = env->GetArrayLength(samples_arr);
    jfloat * samples = env->GetFloatArrayElements(samples_arr, nullptr);
    if (samples == nullptr) {
        return env->NewStringUTF("");
    }

    const char * lang = env->GetStringUTFChars(language, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_timestamps = false;
    params.language = (lang != nullptr && lang[0] != '\0') ? lang : "auto";
    if (n_threads > 0) {
        params.n_threads = n_threads;
    }
    if (cancel_handle != 0) {
        params.abort_callback = abort_if_requested;
        params.abort_callback_user_data = reinterpret_cast<std::atomic<bool> *>(cancel_handle);
    }

    int rc = whisper_full(ctx, params, samples, static_cast<int>(len));

    const bool aborted = rc != 0;
    const int n_segments = whisper_full_n_segments(ctx);
    const int lang_id = whisper_full_lang_id(ctx);
    const char * detected = lang_id >= 0 ? whisper_lang_str(lang_id) : "?";

    // Build the serialized result before the JNI array pointer is released.
    std::string out;
    out.reserve(static_cast<size_t>(n_segments) * 48 + 64);
    out += "lang=";
    out += (aborted ? "~" : "");
    out += detected;
    out += "\n";
    out += std::to_string(n_segments);
    out += "\n";
    for (int i = 0; i < n_segments; ++i) {
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i) * kWhisperTimeUnitMs;
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i) * kWhisperTimeUnitMs;
        const char * text = whisper_full_get_segment_text(ctx, i);
        out += std::to_string(t0);
        out += '\t';
        out += std::to_string(t1);
        out += '\t';
        for (const char * p = text; *p != '\0'; ++p) {
            // Segment text should not carry raw newlines/tabs, but sanitize so
            // the line-based encoding cannot be broken by a model quirk.
            out += (*p == '\n' || *p == '\r' || *p == '\t') ? ' ' : *p;
        }
        out += '\n';
    }

    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(samples_arr, samples, JNI_ABORT);

    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jint JNICALL
Java_com_typewhisper_android_engines_whispercpp_WhisperNative_nativeSampleRate(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    return WHISPER_SAMPLE_RATE;
}

}  // extern "C"

#include "llama.h"

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define TAG "llama-io-prompt"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

std::once_flag g_backend_init_once;
constexpr int MIN_PREDICT_TOKENS = 16;
constexpr int MAX_PREDICT_TOKENS = 4096;
constexpr int MIN_TOP_K = 1;
constexpr int MAX_TOP_K = 100;
constexpr float MIN_TEMP = 0.0f;
constexpr float MAX_TEMP = 2.0f;
constexpr float MIN_TOP_P = 0.1f;
constexpr float MAX_TOP_P = 1.0f;
constexpr uint32_t MIN_CONTEXT_SIZE = 2048;
constexpr uint32_t MAX_CONTEXT_SIZE = 8192;

std::string previewForLog(const std::string & text, size_t limit = 320) {
    std::string compact;
    compact.reserve(std::min(text.size(), limit));
    for (char ch : text) {
        if (ch == '\n' || ch == '\r' || ch == '\t') {
            compact.push_back(' ');
        } else {
            compact.push_back(ch);
        }
        if (compact.size() >= limit) {
            break;
        }
    }
    if (text.size() > limit) {
        compact.append("...");
    }
    return compact;
}

void llamaLogCallback(enum ggml_log_level level, const char * text, void * /* user_data */) {
    if (text == nullptr) {
        return;
    }
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            LOGE("%s", text);
            break;
        case GGML_LOG_LEVEL_WARN:
            LOGI("WARN: %s", text);
            break;
        default:
            break;
    }
}

class Model {
public:
    int modelStatus = 0;
    std::string lastError;

    Model(const std::string & modelPath, int predictTokens)
            : modelPath_(modelPath),
              nPredict_(std::max(MIN_PREDICT_TOKENS, std::min(MAX_PREDICT_TOKENS, predictTokens))) {
        initialize();
    }

    ~Model() {
        shutdownModel();
    }

    std::string runPrompt(const std::string & prompt) {
        std::lock_guard<std::mutex> lock(modelMutex_);
        if (modelStatus != 0) {
            return errorOr("failed to load model");
        }
        if (ctx_ == nullptr || model_ == nullptr || vocab_ == nullptr || sampler_ == nullptr) {
            modelStatus = 1;
            lastError = "model is not initialized";
            return lastError;
        }

        const auto tStart = std::chrono::steady_clock::now();
        LOGI("runPrompt begin input_chars=%zu preview=%s",
             prompt.size(),
             previewForLog(prompt).c_str());

        llama_memory_clear(llama_get_memory(ctx_), true);
        llama_sampler_reset(sampler_);

        const bool addBos = false;
        const bool parseSpecial = true;

        const auto tTokenizeStart = std::chrono::steady_clock::now();
        const int nPromptTokens = -llama_tokenize(vocab_, prompt.c_str(), prompt.size(), nullptr, 0, addBos, parseSpecial);
        if (nPromptTokens <= 0) {
            modelStatus = 1;
            lastError = "failed to tokenize prompt";
            return lastError;
        }

        std::vector<llama_token> promptTokens(nPromptTokens);
        const int tokenized = llama_tokenize(
                vocab_,
                prompt.c_str(),
                prompt.size(),
                promptTokens.data(),
                promptTokens.size(),
                addBos,
                parseSpecial);
        if (tokenized < 0) {
            modelStatus = 1;
            lastError = "failed to tokenize prompt";
            return lastError;
        }
        const auto tTokenizeEnd = std::chrono::steady_clock::now();

        llama_batch batch = llama_batch_get_one(promptTokens.data(), promptTokens.size());
        std::string output;
        output.reserve(static_cast<size_t>(nPredict_) * 4);
        int generatedTokens = 0;
        const auto tDecodeStart = std::chrono::steady_clock::now();
        const int nCtx = llama_n_ctx(ctx_);
        int consumedTokens = 0;

        if (batch.n_tokens >= nCtx) {
            modelStatus = 1;
            lastError = "context size exceeded";
            LOGE("runPrompt context limit n_ctx=%d prompt_tokens=%d",
                 nCtx, batch.n_tokens);
            return lastError;
        }

        for (int i = 0; i < nPredict_; ++i) {
            if (consumedTokens + batch.n_tokens >= nCtx) {
                modelStatus = 1;
                lastError = "context size exceeded";
                LOGE("runPrompt context limit n_ctx=%d consumed_tokens=%d batch_tokens=%d",
                     nCtx, consumedTokens, batch.n_tokens);
                return lastError;
            }

            const int ret = llama_decode(ctx_, batch);
            if (ret != 0) {
                modelStatus = 1;
                lastError = "failed to decode model";
                return lastError;
            }
            consumedTokens += batch.n_tokens;

            llama_token newToken = llama_sampler_sample(sampler_, ctx_, -1);
            if (llama_vocab_is_eog(vocab_, newToken)) {
                break;
            }

            char pieceBuf[256];
            const int pieceLen = llama_token_to_piece(vocab_, newToken, pieceBuf, sizeof(pieceBuf), 0, true);
            if (pieceLen < 0) {
                modelStatus = 1;
                lastError = "failed to convert token to text";
                return lastError;
            }

            output.append(pieceBuf, static_cast<size_t>(pieceLen));
            generatedTokens++;

            batch = llama_batch_get_one(&newToken, 1);
        }
        const auto tDecodeEnd = std::chrono::steady_clock::now();

        modelStatus = 0;
        if (output.empty()) {
            output = "";
        }

        const auto tokenizeMs = std::chrono::duration_cast<std::chrono::milliseconds>(tTokenizeEnd - tTokenizeStart).count();
        const auto decodeMs = std::chrono::duration_cast<std::chrono::milliseconds>(tDecodeEnd - tDecodeStart).count();
        const auto totalMs = std::chrono::duration_cast<std::chrono::milliseconds>(tDecodeEnd - tStart).count();
        const double tps = decodeMs > 0 ? (generatedTokens * 1000.0) / static_cast<double>(decodeMs) : 0.0;
        LOGI("runPrompt done prompt_tokens=%d generated_tokens=%d tokenize_ms=%lld decode_ms=%lld total_ms=%lld tok_per_s=%.2f output_chars=%zu",
             tokenized,
             generatedTokens,
             static_cast<long long>(tokenizeMs),
             static_cast<long long>(decodeMs),
             static_cast<long long>(totalMs),
             tps,
             output.size());

        return output;
    }

    bool updateParams(int predictTokens, float temperature, int topK, float topP) {
        std::lock_guard<std::mutex> lock(modelMutex_);
        if (model_ == nullptr || ctx_ == nullptr) {
            modelStatus = 1;
            lastError = "model is not initialized";
            return false;
        }

        nPredict_ = clampInt(predictTokens, MIN_PREDICT_TOKENS, MAX_PREDICT_TOKENS);
        temperature_ = clampFloat(temperature, MIN_TEMP, MAX_TEMP);
        topK_ = clampInt(topK, MIN_TOP_K, MAX_TOP_K);
        topP_ = clampFloat(topP, MIN_TOP_P, MAX_TOP_P);

        if (!rebuildSampler()) {
            modelStatus = 1;
            if (lastError.empty()) {
                lastError = "failed to rebuild sampler";
            }
            return false;
        }

        modelStatus = 0;
        lastError.clear();
        LOGI("updateParams applied n_predict=%d temp=%.2f top_k=%d top_p=%.2f",
             nPredict_, temperature_, topK_, topP_);
        return true;
    }

    void shutdownModel() {
        std::lock_guard<std::mutex> lock(modelMutex_);
        if (sampler_ != nullptr) {
            llama_sampler_free(sampler_);
            sampler_ = nullptr;
        }
        if (ctx_ != nullptr) {
            llama_free(ctx_);
            ctx_ = nullptr;
        }
        if (model_ != nullptr) {
            llama_model_free(model_);
            model_ = nullptr;
        }
    }

private:
    std::string modelPath_;
    int nPredict_;

    llama_model * model_ = nullptr;
    llama_context * ctx_ = nullptr;
    llama_sampler * sampler_ = nullptr;
    const llama_vocab * vocab_ = nullptr;
    int nThreads_ = 1;
    int nThreadsBatch_ = 1;
    int topK_ = 40;
    float topP_ = 0.9f;
    float temperature_ = 0.8f;
    std::mutex modelMutex_;

    std::string errorOr(const std::string & fallback) const {
        return lastError.empty() ? fallback : lastError;
    }

    static int clampInt(int value, int minValue, int maxValue) {
        return std::max(minValue, std::min(maxValue, value));
    }

    static float clampFloat(float value, float minValue, float maxValue) {
        return std::max(minValue, std::min(maxValue, value));
    }

    bool fileExistsAndReadable(const std::string & path) {
        std::ifstream file(path, std::ios::binary);
        return file.good();
    }

    bool rebuildSampler() {
        if (sampler_ != nullptr) {
            llama_sampler_free(sampler_);
            sampler_ = nullptr;
        }

        llama_sampler_chain_params chainParams = llama_sampler_chain_default_params();
        chainParams.no_perf = true;
        sampler_ = llama_sampler_chain_init(chainParams);
        if (sampler_ == nullptr) {
            lastError = "failed to create sampler chain";
            return false;
        }

        llama_sampler_chain_add(sampler_, llama_sampler_init_top_k(topK_));
        llama_sampler_chain_add(sampler_, llama_sampler_init_top_p(topP_, 1));
        llama_sampler_chain_add(sampler_, llama_sampler_init_temp(temperature_));
        llama_sampler_chain_add(sampler_, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        return true;
    }

    void initialize() {
        if (modelPath_.empty()) {
            modelStatus = 1;
            lastError = "model path is empty";
            return;
        }

        if (!fileExistsAndReadable(modelPath_)) {
            modelStatus = 1;
            lastError = "model file not found: " + modelPath_;
            return;
        }

        std::call_once(g_backend_init_once, []() {
            llama_backend_init();
            ggml_backend_load_all();
            llama_log_set(llamaLogCallback, nullptr);
            LOGI("llama backend initialized");
        });

        llama_model_params modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;

        model_ = llama_model_load_from_file(modelPath_.c_str(), modelParams);
        if (model_ == nullptr) {
            modelStatus = 1;
            lastError = "failed to load model: " + modelPath_;
            return;
        }

        vocab_ = llama_model_get_vocab(model_);
        if (vocab_ == nullptr) {
            modelStatus = 1;
            lastError = "failed to get model vocab";
            return;
        }

        llama_context_params ctxParams = llama_context_default_params();
        const unsigned int cpuCores = std::max(1u, std::thread::hardware_concurrency());
        nThreadsBatch_ = static_cast<int>(cpuCores);
        nThreads_ = static_cast<int>(cpuCores);
        const int desiredCtx = nPredict_ + 1024;
        ctxParams.n_ctx = static_cast<uint32_t>(clampInt(desiredCtx,
                                                         static_cast<int>(MIN_CONTEXT_SIZE),
                                                         static_cast<int>(MAX_CONTEXT_SIZE)));
        ctxParams.n_batch = std::min<uint32_t>(ctxParams.n_ctx, 1024);
        ctxParams.n_threads = nThreads_;
        ctxParams.n_threads_batch = nThreadsBatch_;
        ctxParams.no_perf = true;

        ctx_ = llama_init_from_model(model_, ctxParams);
        if (ctx_ == nullptr) {
            modelStatus = 1;
            lastError = "failed to create model context";
            return;
        }
        llama_set_n_threads(ctx_, nThreads_, nThreadsBatch_);

        if (!rebuildSampler()) {
            modelStatus = 1;
            return;
        }

        modelStatus = 0;
        LOGI("Model initialized path=%s n_predict=%d n_ctx=%u n_batch=%u n_threads=%d n_threads_batch=%d temp=%.2f top_k=%d top_p=%.2f",
             modelPath_.c_str(),
             nPredict_,
             ctxParams.n_ctx,
             ctxParams.n_batch,
             nThreads_,
             nThreadsBatch_,
             temperature_,
             topK_,
             topP_);
    }
};

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_sx_llama_jni_MainActivity_createIOLLModel(JNIEnv * env, jobject /* this */, jstring modelPath, jint nInt) {
    if (modelPath == nullptr) {
        LOGE("createIOLLModel failed: null modelPath");
        return 0;
    }

    const char * rawPath = env->GetStringUTFChars(modelPath, JNI_FALSE);
    std::string path = rawPath == nullptr ? "" : std::string(rawPath);
    env->ReleaseStringUTFChars(modelPath, rawPath);

    LOGI("createIOLLModel start path=%s n_predict=%d", path.c_str(), static_cast<int>(nInt));

    auto * model = new Model(path, static_cast<int>(nInt));
    if (model->modelStatus != 0) {
        LOGE("createIOLLModel failed status=%d error=%s", model->modelStatus, model->lastError.c_str());
        delete model;
        return 0;
    }

    LOGI("createIOLLModel success ptr=%p", static_cast<void *>(model));
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sx_llama_jni_MainActivity_runIOLLModel(JNIEnv * env, jobject /* this */, jlong modelPtr, jstring promptStr) {
    if (modelPtr == 0) {
        const char * err = "failed to load model: model pointer is null";
        LOGE("runIOLLModel failed: %s", err);
        return env->NewStringUTF(err);
    }
    if (promptStr == nullptr) {
        const char * err = "failed to run model: prompt is null";
        LOGE("runIOLLModel failed: %s", err);
        return env->NewStringUTF(err);
    }

    const char * rawPrompt = env->GetStringUTFChars(promptStr, JNI_FALSE);
    std::string prompt = rawPrompt == nullptr ? "" : std::string(rawPrompt);
    env->ReleaseStringUTFChars(promptStr, rawPrompt);

    auto * model = reinterpret_cast<Model *>(modelPtr);
    LOGI("runIOLLModel start ptr=%p prompt_chars=%zu preview=%s",
         static_cast<void *>(model),
         prompt.size(),
         previewForLog(prompt).c_str());
    LOGI("runIOLLModel note=non_streaming_jni_call response_returns_after_full_generation");

    std::string output = model->runPrompt(prompt);
    if (model->modelStatus != 0) {
        LOGE("runIOLLModel failed status=%d error=%s", model->modelStatus, output.c_str());
    } else {
        LOGI("runIOLLModel success output_chars=%zu", output.size());
    }

    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sx_llama_jni_MainActivity_updateIOLLParams(JNIEnv *, jobject /* this */, jlong modelPtr,
                                                     jint maxTokens, jfloat temperature,
                                                     jint topK, jfloat topP) {
    if (modelPtr == 0) {
        LOGE("updateIOLLParams failed: null model pointer");
        return JNI_FALSE;
    }

    auto * model = reinterpret_cast<Model *>(modelPtr);
    bool ok = model->updateParams(static_cast<int>(maxTokens),
                                  static_cast<float>(temperature),
                                  static_cast<int>(topK),
                                  static_cast<float>(topP));
    if (!ok) {
        LOGE("updateIOLLParams failed maxTokens=%d temp=%.2f topK=%d topP=%.2f",
             static_cast<int>(maxTokens),
             static_cast<double>(temperature),
             static_cast<int>(topK),
             static_cast<double>(topP));
        return JNI_FALSE;
    }

    LOGI("updateIOLLParams success maxTokens=%d temp=%.2f topK=%d topP=%.2f",
         static_cast<int>(maxTokens),
         static_cast<double>(temperature),
         static_cast<int>(topK),
         static_cast<double>(topP));
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sx_llama_jni_MainActivity_releaseIOLLModel(JNIEnv *, jobject /* this */, jlong modelPtr) {
    if (modelPtr == 0) {
        LOGI("releaseIOLLModel skipped: null pointer");
        return;
    }

    auto * model = reinterpret_cast<Model *>(modelPtr);
    LOGI("releaseIOLLModel start ptr=%p", static_cast<void *>(model));
    delete model;
    LOGI("releaseIOLLModel done");
}

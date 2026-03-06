package com.sx.llama.jni;

import android.content.Context;
import android.content.SharedPreferences;

public class LlmSettingsStore {
    private static final String PREF_NAME = "llm_inference_settings";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_TOP_K = "top_k";
    private static final String KEY_TOP_P = "top_p";

    private final SharedPreferences preferences;

    public LlmSettingsStore(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public LlmInferenceSettings load() {
        LlmInferenceSettings defaults = LlmInferenceSettings.defaults();
        int maxTokens = preferences.getInt(KEY_MAX_TOKENS, defaults.maxTokens);
        float temperature = preferences.getFloat(KEY_TEMPERATURE, defaults.temperature);
        int topK = preferences.getInt(KEY_TOP_K, defaults.topK);
        float topP = preferences.getFloat(KEY_TOP_P, defaults.topP);
        return new LlmInferenceSettings(maxTokens, temperature, topK, topP).clamp();
    }

    public void save(LlmInferenceSettings settings) {
        LlmInferenceSettings safe = settings == null ? LlmInferenceSettings.defaults() : settings.clamp();
        preferences.edit()
                .putInt(KEY_MAX_TOKENS, safe.maxTokens)
                .putFloat(KEY_TEMPERATURE, safe.temperature)
                .putInt(KEY_TOP_K, safe.topK)
                .putFloat(KEY_TOP_P, safe.topP)
                .apply();
    }
}

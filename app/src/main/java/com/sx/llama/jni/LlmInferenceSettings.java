package com.sx.llama.jni;

public class LlmInferenceSettings {
    public final int maxTokens;
    public final float temperature;
    public final int topK;
    public final float topP;

    public LlmInferenceSettings(int maxTokens, float temperature, int topK, float topP) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
    }

    public static LlmInferenceSettings defaults() {
        return new LlmInferenceSettings(128, 0.8f, 40, 0.9f);
    }

    public LlmInferenceSettings clamp() {
        int safeMaxTokens = Math.max(16, Math.min(4096, maxTokens));
        float safeTemp = Math.max(0.0f, Math.min(2.0f, temperature));
        int safeTopK = Math.max(1, Math.min(100, topK));
        float safeTopP = Math.max(0.1f, Math.min(1.0f, topP));
        return new LlmInferenceSettings(safeMaxTokens, safeTemp, safeTopK, safeTopP);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LlmInferenceSettings)) {
            return false;
        }
        LlmInferenceSettings other = (LlmInferenceSettings) obj;
        return maxTokens == other.maxTokens
                && Float.compare(temperature, other.temperature) == 0
                && topK == other.topK
                && Float.compare(topP, other.topP) == 0;
    }

    @Override
    public int hashCode() {
        int result = maxTokens;
        result = 31 * result + Float.floatToIntBits(temperature);
        result = 31 * result + topK;
        result = 31 * result + Float.floatToIntBits(topP);
        return result;
    }
}

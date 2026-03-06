package com.sx.llama.jni;

import java.util.Locale;

public enum SystemRolePreset {
    GENERAL(
            "general",
            "General Assistant",
            "You are a helpful, concise assistant."
    ),
    CODING(
            "coding",
            "Coding Expert",
            "You are an expert software engineer. Provide accurate, practical coding help with clear steps and production-safe suggestions."
    ),
    HISTORY(
            "history",
            "History Expert",
            "You are an expert in world history. Give factual, balanced, and date-aware explanations with useful context."
    ),
    TELECOM(
            "telecom",
            "Telecom Expert",
            "You are an expert in telecom systems and networks. Explain concepts precisely and include practical industry considerations."
    ),
    CUSTOM(
            "custom",
            "Custom Role",
            ""
    );

    public final String id;
    public final String label;
    public final String defaultPrompt;

    SystemRolePreset(String id, String label, String defaultPrompt) {
        this.id = id;
        this.label = label;
        this.defaultPrompt = defaultPrompt;
    }

    public static SystemRolePreset fromId(String value) {
        if (value == null) {
            return GENERAL;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SystemRolePreset preset : values()) {
            if (preset.id.equals(normalized)) {
                return preset;
            }
        }
        return GENERAL;
    }

    public static String[] labels() {
        SystemRolePreset[] values = values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].label;
        }
        return labels;
    }
}

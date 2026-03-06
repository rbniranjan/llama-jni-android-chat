package com.sx.llama.jni;

import android.text.TextUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PromptTemplateEngine {
    private static final String DEFAULT_SYSTEM = "You are a helpful, concise assistant.";
    private static final int SUMMARY_TEXT_LIMIT = 220;

    private PromptTemplateEngine() {
    }

    public static PromptFormatType resolveFormat(ModelInfo model) {
        if (model == null) {
            return PromptFormatType.PLAIN;
        }

        String hinted = model.promptFormatHint == null ? "" : model.promptFormatHint.trim().toLowerCase(Locale.ROOT);
        if (!hinted.isEmpty()) {
            switch (hinted) {
                case "llama3_instruct":
                    return PromptFormatType.LLAMA3_INSTRUCT;
                case "qwen_chatml":
                    return PromptFormatType.QWEN_CHATML;
                case "gemma_turn":
                    return PromptFormatType.GEMMA_TURN;
                case "phi3_chat":
                    return PromptFormatType.PHI3_CHAT;
                case "plain":
                default:
                    return PromptFormatType.PLAIN;
            }
        }

        String key = (model.id + " " + model.name).toLowerCase(Locale.ROOT);
        if (key.contains("llama-3") || key.contains("llama3")) {
            return PromptFormatType.LLAMA3_INSTRUCT;
        }
        if (key.contains("qwen")) {
            return PromptFormatType.QWEN_CHATML;
        }
        if (key.contains("gemma")) {
            return PromptFormatType.GEMMA_TURN;
        }
        if (key.contains("phi-3") || key.contains("phi3")) {
            return PromptFormatType.PHI3_CHAT;
        }
        return PromptFormatType.PLAIN;
    }

    public static String buildPrompt(ModelInfo model, String userText) {
        return buildPrompt(model, userText, Collections.emptyList(), 0);
    }

    public static String buildPrompt(ModelInfo model,
                                     String userText,
                                     List<ChatTurn> turns,
                                     int maxHistoryTurns) {
        String text = userText == null ? "" : userText.trim();
        if (TextUtils.isEmpty(text)) {
            return "";
        }

        String historySummary = buildHistorySummary(turns, maxHistoryTurns);
        switch (resolveFormat(model)) {
            case LLAMA3_INSTRUCT:
                return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n"
                        + withHistoryInSystem(DEFAULT_SYSTEM, historySummary)
                        + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n"
                        + text
                        + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n";
            case QWEN_CHATML:
                return "<|im_start|>system\n"
                        + withHistoryInSystem(DEFAULT_SYSTEM, historySummary)
                        + "<|im_end|>\n<|im_start|>user\n"
                        + text
                        + "<|im_end|>\n<|im_start|>assistant\n";
            case GEMMA_TURN:
                return "<start_of_turn>user\n"
                        + withHistoryInUser(historySummary, text)
                        + "<end_of_turn>\n<start_of_turn>model\n";
            case PHI3_CHAT:
                return "<|system|>\n"
                        + withHistoryInSystem(DEFAULT_SYSTEM, historySummary)
                        + "<|end|>\n<|user|>\n"
                        + text
                        + "<|end|>\n<|assistant|>\n";
            case PLAIN:
            default:
                return withHistoryInSystem(DEFAULT_SYSTEM, historySummary)
                        + "\nUser: " + text + "\nAssistant:";
        }
    }

    private static String withHistoryInSystem(String system, String historySummary) {
        if (TextUtils.isEmpty(historySummary)) {
            return system;
        }
        return system
                + "\n\nUse this recent conversation summary for follow-up context:\n"
                + historySummary;
    }

    private static String withHistoryInUser(String historySummary, String userText) {
        if (TextUtils.isEmpty(historySummary)) {
            return userText;
        }
        return "Recent conversation summary:\n"
                + historySummary
                + "\n\nCurrent user question:\n"
                + userText;
    }

    private static String buildHistorySummary(List<ChatTurn> turns, int maxHistoryTurns) {
        if (turns == null || turns.isEmpty() || maxHistoryTurns <= 0) {
            return "";
        }

        int end = turns.size();
        int start = Math.max(0, end - maxHistoryTurns);

        StringBuilder builder = new StringBuilder();
        int summaryIndex = 1;
        for (int i = start; i < end; i++) {
            ChatTurn turn = turns.get(i);
            if (turn == null) {
                continue;
            }
            String user = compact(turn.userMessage);
            String assistant = compact(turn.assistantMessage);
            if (TextUtils.isEmpty(user) && TextUtils.isEmpty(assistant)) {
                continue;
            }
            builder.append("Turn ")
                    .append(summaryIndex++)
                    .append(":\n")
                    .append("User: ")
                    .append(truncate(user))
                    .append("\n")
                    .append("Assistant: ")
                    .append(truncate(assistant))
                    .append("\n");
        }

        return builder.toString().trim();
    }

    private static String compact(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value) {
        if (TextUtils.isEmpty(value) || value.length() <= SUMMARY_TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, SUMMARY_TEXT_LIMIT) + "...";
    }

    public static String extractAssistantText(ModelInfo model, String rawOutput, String formattedPrompt, String userInput) {
        if (rawOutput == null) {
            return "";
        }

        String cleaned = rawOutput;

        if (!TextUtils.isEmpty(formattedPrompt) && cleaned.startsWith(formattedPrompt)) {
            cleaned = cleaned.substring(formattedPrompt.length());
        }

        // Remove common template residue tokens if present in output.
        cleaned = cleaned
                .replace("<|im_start|>assistant", "")
                .replace("<|im_end|>", "")
                .replace("<|assistant|>", "")
                .replace("<|end|>", "")
                .replace("<start_of_turn>model", "")
                .replace("<end_of_turn>", "")
                .replace("<|eot_id|>", "")
                .replace("<|start_header_id|>assistant<|end_header_id|>", "");

        String userText = userInput == null ? "" : userInput.trim();
        if (!TextUtils.isEmpty(userText) && cleaned.startsWith(userText)) {
            cleaned = cleaned.substring(userText.length());
        }

        return cleaned.trim();
    }
}

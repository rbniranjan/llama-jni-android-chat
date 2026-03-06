package com.sx.llama.jni;

public class ChatMessage {
    public enum Role {
        USER,
        ASSISTANT
    }

    public final Role role;
    public String text;

    public ChatMessage(Role role, String text) {
        this.role = role;
        this.text = text == null ? "" : text;
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text);
    }
}

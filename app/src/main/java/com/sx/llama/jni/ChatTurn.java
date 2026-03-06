package com.sx.llama.jni;

public class ChatTurn {
    public final String userMessage;
    public final String assistantMessage;

    public ChatTurn(String userMessage, String assistantMessage) {
        this.userMessage = userMessage == null ? "" : userMessage;
        this.assistantMessage = assistantMessage == null ? "" : assistantMessage;
    }
}

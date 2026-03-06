package com.sx.llama.jni;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sx.llama.jni.databinding.ItemChatAssistantBinding;
import com.sx.llama.jni.databinding.ItemChatUserBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_USER = 1;
    private static final int TYPE_ASSISTANT = 2;

    private final List<ChatMessage> items = new ArrayList<>();

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = items.get(position);
        return message.role == ChatMessage.Role.USER ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            ItemChatUserBinding binding = ItemChatUserBinding.inflate(inflater, parent, false);
            return new UserMessageViewHolder(binding);
        }
        ItemChatAssistantBinding binding = ItemChatAssistantBinding.inflate(inflater, parent, false);
        return new AssistantMessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = items.get(position);
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).binding.tvMessage.setText(message.text);
            return;
        }
        ((AssistantMessageViewHolder) holder).binding.tvMessage.setText(message.text);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public int addMessage(ChatMessage message) {
        items.add(message);
        int index = items.size() - 1;
        notifyItemInserted(index);
        return index;
    }

    public void updateMessageText(int position, String text) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        ChatMessage message = items.get(position);
        message.text = text == null ? "" : text;
        notifyItemChanged(position);
    }

    public List<ChatMessage> getItems() {
        return new ArrayList<>(items);
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        final ItemChatUserBinding binding;

        UserMessageViewHolder(ItemChatUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        final ItemChatAssistantBinding binding;

        AssistantMessageViewHolder(ItemChatAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

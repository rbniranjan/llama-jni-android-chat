package com.sx.llama.jni;

import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sx.llama.jni.databinding.ItemModelBinding;

import java.util.ArrayList;
import java.util.List;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ModelViewHolder> {
    public interface ModelActionListener {
        void onDownload(ModelInfo model);

        void onCancel(ModelInfo model);

        void onSelect(ModelInfo model);

        void onDelete(ModelInfo model);
    }

    private final ModelActionListener listener;
    private final List<ModelInfo> items = new ArrayList<>();
    private String selectedModelId = "";

    public ModelAdapter(ModelActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ModelInfo> models) {
        items.clear();
        items.addAll(models);
        notifyDataSetChanged();
    }

    public void setSelectedModelId(String selectedModelId) {
        this.selectedModelId = selectedModelId == null ? "" : selectedModelId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemModelBinding binding = ItemModelBinding.inflate(inflater, parent, false);
        return new ModelViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
        ModelInfo model = items.get(position);
        holder.binding.tvModelName.setText(model.name);

        String sizeText = model.catalogSizeBytes > 0
                ? Formatter.formatShortFileSize(holder.itemView.getContext(), model.catalogSizeBytes)
                : "Unknown size";
        holder.binding.tvModelMeta.setText(model.quant + " • " + sizeText);

        boolean isSelected = TextUtils.equals(selectedModelId, model.id);

        if (model.downloading) {
            holder.binding.tvModelStatus.setText(model.downloadProgress > 0
                    ? "Downloading " + model.downloadProgress + "%"
                    : "Downloading...");
            holder.binding.progressDownload.setVisibility(View.VISIBLE);
            holder.binding.progressDownload.setIndeterminate(model.downloadProgress <= 0);
            holder.binding.progressDownload.setProgress(model.downloadProgress);

            holder.binding.btnPrimary.setText("Cancel");
            holder.binding.btnPrimary.setEnabled(true);
            holder.binding.btnPrimary.setOnClickListener(v -> listener.onCancel(model));

            holder.binding.btnSecondary.setVisibility(View.GONE);
            holder.binding.btnSecondary.setOnClickListener(null);
            return;
        }

        holder.binding.progressDownload.setVisibility(View.GONE);
        holder.binding.progressDownload.setIndeterminate(false);

        if (model.downloaded) {
            holder.binding.tvModelStatus.setText(isSelected ? "Downloaded • Selected" : "Downloaded");

            holder.binding.btnPrimary.setText(isSelected ? "Selected" : "Select");
            holder.binding.btnPrimary.setEnabled(true);
            holder.binding.btnPrimary.setOnClickListener(v -> listener.onSelect(model));

            holder.binding.btnSecondary.setVisibility(View.VISIBLE);
            holder.binding.btnSecondary.setText("Delete");
            holder.binding.btnSecondary.setOnClickListener(v -> listener.onDelete(model));
            return;
        }

        holder.binding.tvModelStatus.setText("Not downloaded");
        holder.binding.btnPrimary.setText("Download");
        holder.binding.btnPrimary.setEnabled(true);
        holder.binding.btnPrimary.setOnClickListener(v -> listener.onDownload(model));

        holder.binding.btnSecondary.setVisibility(View.GONE);
        holder.binding.btnSecondary.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ModelViewHolder extends RecyclerView.ViewHolder {
        private final ItemModelBinding binding;

        ModelViewHolder(ItemModelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

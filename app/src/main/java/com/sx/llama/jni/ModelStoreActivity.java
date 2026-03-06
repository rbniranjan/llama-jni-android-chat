package com.sx.llama.jni;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sx.llama.jni.databinding.ActivityModelStoreBinding;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ModelStoreActivity extends AppCompatActivity implements ModelAdapter.ModelActionListener {
    private static final String TAG = "ModelStore";
    private ActivityModelStoreBinding binding;
    private ModelCatalogLoader catalogLoader;
    private ModelStateStore stateStore;
    private ModelDownloadHelper downloadHelper;
    private ModelAdapter adapter;

    private final List<ModelInfo> models = new ArrayList<>();
    private final Map<Long, String> activeDownloadModelIds = new HashMap<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean pollerRunning = false;

    private final Runnable progressPoller = new Runnable() {
        @Override
        public void run() {
            if (!pollerRunning) {
                return;
            }
            pollDownloadProgress();
            if (pollerRunning) {
                uiHandler.postDelayed(this, 700L);
            }
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (activeDownloadModelIds.containsKey(downloadId)) {
                pollDownloadProgress();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityModelStoreBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.i(TAG, "onCreate package=" + getPackageName());

        catalogLoader = new ModelCatalogLoader(this);
        stateStore = new ModelStateStore(this);
        downloadHelper = new ModelDownloadHelper(this);

        adapter = new ModelAdapter(this);
        binding.recyclerModels.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerModels.setAdapter(adapter);

        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, downloadFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, downloadFilter);
        }

        loadModels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadModels();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressPoller();
        unregisterReceiver(downloadReceiver);
        Log.i(TAG, "onDestroy");
    }

    private void loadModels() {
        List<ModelInfo> loaded;
        try {
            loaded = catalogLoader.loadModels();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "loadModels failed", e);
            Toast.makeText(this, "Failed to load model catalog", Toast.LENGTH_LONG).show();
            return;
        }

        models.clear();
        models.addAll(loaded);

        for (ModelInfo model : models) {
            mergePersistedState(model);
        }

        adapter.setItems(models);
        adapter.setSelectedModelId(stateStore.getSelectedModelId());
        updateSelectedLabel();
        Log.i(TAG, "loadModels success count=" + models.size());
    }

    private void mergePersistedState(ModelInfo model) {
        File expected = downloadHelper.getModelFile(model);
        ModelStateStore.ModelState state = stateStore.readModelState(model.id);

        if (expected != null && expected.exists() && expected.length() <= 0L) {
            // Remove failed/partial downloads so retry starts cleanly.
            //noinspection ResultOfMethodCallIgnored
            expected.delete();
            Log.w(TAG, "Deleted zero-byte model file id=" + model.id + " path=" + expected.getAbsolutePath());
        }

        if (expected != null && expected.exists() && expected.length() > 0L) {
            model.downloaded = true;
            model.localPath = expected.getAbsolutePath();
            model.persistedSizeBytes = expected.length();
            model.downloadedAt = state.downloadedAt > 0L ? state.downloadedAt : expected.lastModified();

            if (!state.downloaded || TextUtils.isEmpty(state.localPath)) {
                stateStore.saveModelState(model.id, true, model.localPath, model.persistedSizeBytes, model.downloadedAt);
            }
            Log.i(TAG, "Model available id=" + model.id + " path=" + model.localPath + " size=" + model.persistedSizeBytes);
            return;
        }

        if (state.downloaded) {
            stateStore.clearModelState(model.id);
            Log.w(TAG, "Prefs said downloaded but file missing. Reset id=" + model.id);
        }

        model.downloaded = false;
        model.localPath = "";
        model.persistedSizeBytes = 0L;
        model.downloadedAt = 0L;
    }

    @Override
    public void onDownload(ModelInfo model) {
        if (model.downloading || model.downloaded) {
            Log.d(TAG, "Ignoring download request id=" + model.id + " downloading=" + model.downloading + " downloaded=" + model.downloaded);
            return;
        }

        File targetFile = downloadHelper.getModelFile(model);
        if (targetFile == null) {
            Log.e(TAG, "External storage unavailable for model id=" + model.id);
            Toast.makeText(this, "External storage unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "Failed to create models dir path=" + parent.getAbsolutePath());
            Toast.makeText(this, "Unable to create models directory", Toast.LENGTH_LONG).show();
            return;
        }

        if (parent != null && model.catalogSizeBytes > 0L) {
            long availableBytes;
            try {
                StatFs statFs = new StatFs(parent.getAbsolutePath());
                availableBytes = statFs.getAvailableBytes();
            } catch (IllegalArgumentException ex) {
                availableBytes = -1L;
            }

            // Keep a small buffer for filesystem metadata and temp writes.
            long requiredBytes = model.catalogSizeBytes + (50L * 1024L * 1024L);
            if (availableBytes >= 0L && availableBytes < requiredBytes) {
                String requiredText = Formatter.formatFileSize(this, requiredBytes);
                String availableText = Formatter.formatFileSize(this, availableBytes);
                Toast.makeText(this,
                        "Not enough storage. Required: " + requiredText + ", available: " + availableText,
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Insufficient space before enqueue modelId=" + model.id
                        + " requiredBytes=" + requiredBytes + " availableBytes=" + availableBytes);
                return;
            }
        }

        if (targetFile.exists() && targetFile.length() > 0L) {
            markModelDownloaded(model, targetFile);
            adapter.notifyDataSetChanged();
            updateSelectedLabel();
            return;
        }
        if (targetFile.exists() && targetFile.length() <= 0L) {
            //noinspection ResultOfMethodCallIgnored
            targetFile.delete();
            Log.w(TAG, "Deleted existing zero-byte file before download id=" + model.id);
        }

        long downloadId = downloadHelper.enqueueDownload(model);
        if (downloadId <= 0L) {
            Log.e(TAG, "enqueueDownload returned invalid id for model=" + model.id);
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_LONG).show();
            return;
        }

        model.downloading = true;
        model.downloadProgress = 0;
        model.downloadRequestId = downloadId;

        activeDownloadModelIds.put(downloadId, model.id);
        Log.i(TAG, "Download started modelId=" + model.id + " requestId=" + downloadId + " file=" + targetFile.getAbsolutePath());
        adapter.notifyDataSetChanged();
        startProgressPoller();
    }

    @Override
    public void onCancel(ModelInfo model) {
        if (!model.downloading) {
            return;
        }

        if (model.downloadRequestId > 0L) {
            downloadHelper.cancelDownload(model.downloadRequestId);
            activeDownloadModelIds.remove(model.downloadRequestId);
            Log.i(TAG, "Download canceled modelId=" + model.id + " requestId=" + model.downloadRequestId);
        } else {
            removeActiveDownloadForModel(model.id);
        }

        model.downloading = false;
        model.downloadProgress = 0;
        model.downloadRequestId = -1L;
        adapter.notifyDataSetChanged();

        if (activeDownloadModelIds.isEmpty()) {
            stopProgressPoller();
        }
    }

    @Override
    public void onSelect(ModelInfo model) {
        if (!model.downloaded) {
            Toast.makeText(this, "Download the model first", Toast.LENGTH_SHORT).show();
            return;
        }

        File modelFile = downloadHelper.getModelFile(model);
        if (modelFile == null || !modelFile.exists() || modelFile.length() <= 0L) {
            model.downloaded = false;
            model.localPath = "";
            stateStore.clearModelState(model.id);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Model file is missing", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Select failed missing file modelId=" + model.id + " path=" + (modelFile == null ? "null" : modelFile.getAbsolutePath()));
            return;
        }

        stateStore.setSelectedModelId(model.id);
        adapter.setSelectedModelId(model.id);
        updateSelectedLabel();
        Log.i(TAG, "Model selected id=" + model.id + " path=" + modelFile.getAbsolutePath() + " size=" + modelFile.length());

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_MODEL_ID, model.id);
        startActivity(intent);
    }

    @Override
    public void onDelete(ModelInfo model) {
        if (model.downloading) {
            onCancel(model);
        }

        File modelFile = downloadHelper.getModelFile(model);
        if (modelFile != null && modelFile.exists() && !modelFile.delete()) {
            Log.e(TAG, "Delete failed modelId=" + model.id + " path=" + modelFile.getAbsolutePath());
            Toast.makeText(this, "Failed to delete model file", Toast.LENGTH_LONG).show();
            return;
        }
        Log.i(TAG, "Model deleted id=" + model.id + " path=" + (modelFile == null ? "null" : modelFile.getAbsolutePath()));

        stateStore.clearModelState(model.id);

        if (TextUtils.equals(stateStore.getSelectedModelId(), model.id)) {
            stateStore.setSelectedModelId(null);
            adapter.setSelectedModelId("");
        }

        model.downloaded = false;
        model.downloading = false;
        model.downloadProgress = 0;
        model.localPath = "";
        model.persistedSizeBytes = 0L;
        model.downloadedAt = 0L;
        model.downloadRequestId = -1L;

        adapter.notifyDataSetChanged();
        updateSelectedLabel();
    }

    private void pollDownloadProgress() {
        List<Long> finishedIds = new ArrayList<>();

        Iterator<Map.Entry<Long, String>> iterator = activeDownloadModelIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, String> entry = iterator.next();
            long downloadId = entry.getKey();
            String modelId = entry.getValue();

            ModelInfo model = findModelById(modelId);
            if (model == null) {
                finishedIds.add(downloadId);
                continue;
            }

            ModelDownloadHelper.DownloadProgress progress = downloadHelper.queryDownload(downloadId);
            if (progress == null) {
                File modelFile = downloadHelper.getModelFile(model);
                if (modelFile != null && modelFile.exists() && modelFile.length() <= 0L) {
                    //noinspection ResultOfMethodCallIgnored
                    modelFile.delete();
                }
                model.downloading = false;
                model.downloadRequestId = -1L;
                finishedIds.add(downloadId);
                Log.w(TAG, "Download row not found requestId=" + downloadId + " modelId=" + model.id);
                continue;
            }

            if (progress.status == DownloadManager.STATUS_RUNNING
                    || progress.status == DownloadManager.STATUS_PENDING
                    || progress.status == DownloadManager.STATUS_PAUSED) {
                model.downloading = true;
                model.downloadProgress = progress.percent;
                model.downloadRequestId = downloadId;
                continue;
            }

            if (progress.status == DownloadManager.STATUS_SUCCESSFUL) {
                File modelFile = downloadHelper.getModelFile(model);
                if (modelFile != null && modelFile.exists() && modelFile.length() > 0L) {
                    markModelDownloaded(model, modelFile);
                    Toast.makeText(this, model.name + " downloaded", Toast.LENGTH_SHORT).show();
                } else {
                    if (modelFile != null && modelFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        modelFile.delete();
                    }
                    model.downloading = false;
                    model.downloadProgress = 0;
                    model.downloadRequestId = -1L;
                    Toast.makeText(this, "Download failed: file missing", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Download successful status but file invalid requestId=" + downloadId + " modelId=" + model.id);
                }
                finishedIds.add(downloadId);
                continue;
            }

            if (progress.status == DownloadManager.STATUS_FAILED) {
                File modelFile = downloadHelper.getModelFile(model);
                if (modelFile != null && modelFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    modelFile.delete();
                }
                model.downloading = false;
                model.downloadProgress = 0;
                model.downloadRequestId = -1L;
                String reason = ModelDownloadHelper.reasonToString(progress.status, progress.reason);
                if (progress.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                    Toast.makeText(this, "Download failed: not enough storage space", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Download failed: " + reason, Toast.LENGTH_LONG).show();
                }
                Log.e(TAG, "Download failed requestId=" + downloadId + " modelId=" + model.id
                        + " reason=" + reason + " code=" + progress.reason);
                finishedIds.add(downloadId);
            }
        }

        for (Long id : finishedIds) {
            activeDownloadModelIds.remove(id);
        }

        adapter.notifyDataSetChanged();
        updateSelectedLabel();

        if (activeDownloadModelIds.isEmpty()) {
            stopProgressPoller();
        }
    }

    private void markModelDownloaded(ModelInfo model, File file) {
        model.downloaded = true;
        model.downloading = false;
        model.downloadProgress = 100;
        model.localPath = file.getAbsolutePath();
        model.persistedSizeBytes = file.length();
        model.downloadedAt = System.currentTimeMillis();
        model.downloadRequestId = -1L;

        stateStore.saveModelState(
                model.id,
                true,
                model.localPath,
                model.persistedSizeBytes,
                model.downloadedAt
        );
        Log.i(TAG, "Model stored locally id=" + model.id + " path=" + model.localPath + " size=" + model.persistedSizeBytes);
    }

    private ModelInfo findModelById(String modelId) {
        for (ModelInfo model : models) {
            if (TextUtils.equals(model.id, modelId)) {
                return model;
            }
        }
        return null;
    }

    private void removeActiveDownloadForModel(String modelId) {
        Iterator<Map.Entry<Long, String>> iterator = activeDownloadModelIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, String> entry = iterator.next();
            if (TextUtils.equals(entry.getValue(), modelId)) {
                iterator.remove();
                return;
            }
        }
    }

    private void startProgressPoller() {
        if (pollerRunning) {
            return;
        }
        pollerRunning = true;
        uiHandler.post(progressPoller);
    }

    private void stopProgressPoller() {
        pollerRunning = false;
        uiHandler.removeCallbacks(progressPoller);
    }

    private void updateSelectedLabel() {
        String selectedModelId = stateStore.getSelectedModelId();
        if (TextUtils.isEmpty(selectedModelId)) {
            binding.tvSelectedModel.setText("Selected model: none");
            return;
        }

        ModelInfo selected = findModelById(selectedModelId);
        if (selected == null) {
            binding.tvSelectedModel.setText("Selected model: none");
            return;
        }

        binding.tvSelectedModel.setText("Selected model: " + selected.name);
        Log.d(TAG, "Selected label updated modelId=" + selected.id);
    }
}

package com.sx.llama.jni;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

public class ModelStateStore {
    private static final String PREF_NAME = "model_store_prefs";
    private static final String KEY_SELECTED_MODEL_ID = "selectedModelId";
    private static final String TAG = "ModelStateStore";

    private final SharedPreferences prefs;

    public ModelStateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public ModelState readModelState(String modelId) {
        String prefix = prefix(modelId);
        boolean downloaded = prefs.getBoolean(prefix + "downloaded", false);
        String localPath = prefs.getString(prefix + "localPath", "");
        long sizeBytes = prefs.getLong(prefix + "sizeBytes", 0L);
        long downloadedAt = prefs.getLong(prefix + "downloadedAt", 0L);
        Log.d(TAG, "readModelState id=" + modelId + " downloaded=" + downloaded
                + " path=" + localPath + " size=" + sizeBytes + " downloadedAt=" + downloadedAt);
        return new ModelState(downloaded, localPath, sizeBytes, downloadedAt);
    }

    public void saveModelState(String modelId, boolean downloaded, String localPath, long sizeBytes, long downloadedAt) {
        String prefix = prefix(modelId);
        prefs.edit()
                .putBoolean(prefix + "downloaded", downloaded)
                .putString(prefix + "localPath", localPath == null ? "" : localPath)
                .putLong(prefix + "sizeBytes", sizeBytes)
                .putLong(prefix + "downloadedAt", downloadedAt)
                .apply();
        Log.i(TAG, "saveModelState id=" + modelId + " downloaded=" + downloaded
                + " path=" + localPath + " size=" + sizeBytes + " downloadedAt=" + downloadedAt);
    }

    public void clearModelState(String modelId) {
        String prefix = prefix(modelId);
        prefs.edit()
                .remove(prefix + "downloaded")
                .remove(prefix + "localPath")
                .remove(prefix + "sizeBytes")
                .remove(prefix + "downloadedAt")
                .apply();
        Log.i(TAG, "clearModelState id=" + modelId);
    }

    public String getSelectedModelId() {
        String value = prefs.getString(KEY_SELECTED_MODEL_ID, "");
        Log.d(TAG, "getSelectedModelId value=" + value);
        return value;
    }

    public void setSelectedModelId(String modelId) {
        if (TextUtils.isEmpty(modelId)) {
            prefs.edit().remove(KEY_SELECTED_MODEL_ID).apply();
            Log.i(TAG, "setSelectedModelId cleared");
            return;
        }
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply();
        Log.i(TAG, "setSelectedModelId id=" + modelId);
    }

    private String prefix(String modelId) {
        return "model." + modelId + ".";
    }

    public static class ModelState {
        public final boolean downloaded;
        public final String localPath;
        public final long sizeBytes;
        public final long downloadedAt;

        public ModelState(boolean downloaded, String localPath, long sizeBytes, long downloadedAt) {
            this.downloaded = downloaded;
            this.localPath = localPath;
            this.sizeBytes = sizeBytes;
            this.downloadedAt = downloadedAt;
        }
    }
}

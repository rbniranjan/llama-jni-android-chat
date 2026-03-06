package com.sx.llama.jni;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public class ModelDownloadHelper {
    private static final String TAG = "ModelDownload";
    private final Context context;
    private final DownloadManager downloadManager;

    public ModelDownloadHelper(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public long enqueueDownload(ModelInfo model) {
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager unavailable. modelId=" + model.id);
            return -1L;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(model.url));
        request.setTitle(model.name);
        request.setDescription(model.filename);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(false);
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Android) llama-demo");
        request.addRequestHeader("Accept", "application/octet-stream");
        request.setDestinationInExternalFilesDir(context, null, "models/" + model.filename);
        long id = downloadManager.enqueue(request);
        Log.i(TAG, "enqueue id=" + id + " modelId=" + model.id + " url=" + model.url);
        return id;
    }

    public void cancelDownload(long downloadId) {
        if (downloadManager == null || downloadId <= 0L) {
            return;
        }
        downloadManager.remove(downloadId);
        Log.i(TAG, "cancel id=" + downloadId);
    }

    public DownloadProgress queryDownload(long downloadId) {
        if (downloadManager == null || downloadId <= 0L) {
            return null;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "query: no row for id=" + downloadId);
                return null;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));

            int percent = 0;
            if (total > 0L) {
                percent = (int) ((downloaded * 100L) / total);
            }

            DownloadProgress progress = new DownloadProgress(status, reason, downloaded, total, percent, localUri);
            Log.d(TAG, "query id=" + downloadId
                    + " status=" + statusToString(status)
                    + " reason=" + reasonToString(status, reason)
                    + " downloaded=" + downloaded + " total=" + total
                    + " percent=" + percent + " localUri=" + localUri);
            return progress;
        }
    }

    public File getModelFile(ModelInfo model) {
        File externalRoot = context.getExternalFilesDir(null);
        if (externalRoot == null) {
            return null;
        }
        File modelsDir = new File(externalRoot, "models");
        return new File(modelsDir, model.filename);
    }

    public static String statusToString(int status) {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
                return "PENDING";
            case DownloadManager.STATUS_RUNNING:
                return "RUNNING";
            case DownloadManager.STATUS_PAUSED:
                return "PAUSED";
            case DownloadManager.STATUS_SUCCESSFUL:
                return "SUCCESSFUL";
            case DownloadManager.STATUS_FAILED:
                return "FAILED";
            default:
                return "UNKNOWN(" + status + ")";
        }
    }

    public static String reasonToString(int status, int reason) {
        if (status == DownloadManager.STATUS_PAUSED) {
            switch (reason) {
                case DownloadManager.PAUSED_WAITING_TO_RETRY:
                    return "PAUSED_WAITING_TO_RETRY";
                case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                    return "PAUSED_WAITING_FOR_NETWORK";
                case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                    return "PAUSED_QUEUED_FOR_WIFI";
                case DownloadManager.PAUSED_UNKNOWN:
                    return "PAUSED_UNKNOWN";
                default:
                    return "PAUSED_REASON(" + reason + ")";
            }
        }
        if (status == DownloadManager.STATUS_FAILED) {
            switch (reason) {
                case DownloadManager.ERROR_CANNOT_RESUME:
                    return "ERROR_CANNOT_RESUME";
                case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                    return "ERROR_DEVICE_NOT_FOUND";
                case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                    return "ERROR_FILE_ALREADY_EXISTS";
                case DownloadManager.ERROR_FILE_ERROR:
                    return "ERROR_FILE_ERROR";
                case DownloadManager.ERROR_HTTP_DATA_ERROR:
                    return "ERROR_HTTP_DATA_ERROR";
                case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                    return "ERROR_INSUFFICIENT_SPACE";
                case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                    return "ERROR_TOO_MANY_REDIRECTS";
                case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                    return "ERROR_UNHANDLED_HTTP_CODE";
                case DownloadManager.ERROR_UNKNOWN:
                    return "ERROR_UNKNOWN";
                default:
                    return "FAILED_REASON(" + reason + ")";
            }
        }
        return String.valueOf(reason);
    }

    public static class DownloadProgress {
        public final int status;
        public final int reason;
        public final long downloadedBytes;
        public final long totalBytes;
        public final int percent;
        public final String localUri;

        public DownloadProgress(int status, int reason, long downloadedBytes, long totalBytes, int percent, String localUri) {
            this.status = status;
            this.reason = reason;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.percent = percent;
            this.localUri = localUri;
        }
    }
}

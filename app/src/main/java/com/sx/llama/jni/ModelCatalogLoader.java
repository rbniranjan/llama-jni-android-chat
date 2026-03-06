package com.sx.llama.jni;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModelCatalogLoader {
    private static final String ASSET_FILE = "models_catalog.json";
    private static final String TAG = "ModelCatalog";

    private final Context context;

    public ModelCatalogLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<ModelInfo> loadModels() throws IOException, JSONException {
        String json = readAsset(ASSET_FILE);
        JSONObject root = new JSONObject(json);
        JSONArray models = root.getJSONArray("models");

        List<ModelInfo> result = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject modelObj = models.getJSONObject(i);
            JSONObject fileObj = modelObj.getJSONObject("file");

            ModelInfo model = new ModelInfo(
                    modelObj.getString("id"),
                    modelObj.getString("name"),
                    modelObj.optString("publisher", ""),
                    modelObj.optString("format", "gguf"),
                    modelObj.optString("quant", ""),
                    modelObj.optString("prompt_format", ""),
                    fileObj.getString("filename"),
                    fileObj.optLong("size_bytes", 0L),
                    fileObj.getString("url")
            );
            result.add(model);
        }
        Log.i(TAG, "Loaded models catalog from assets/" + ASSET_FILE + ", count=" + result.size());
        return result;
    }

    private String readAsset(String fileName) throws IOException {
        try (InputStream inputStream = context.getAssets().open(fileName);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }
}

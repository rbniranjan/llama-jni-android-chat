package com.sx.llama.jni;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sx.llama.jni.databinding.ActivityChatBinding;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_MODEL_ID = "extra_model_id";
    private static final String TAG = "ModelChat";

    private static final int DEFAULT_PREDICT_TOKENS = 128;
    private static final int MIN_STREAM_CHUNK_CHARS = 192;
    private static final int MAX_STREAM_CHUNK_CHARS = 768;
    private static final long FAST_STREAM_DELAY_MS = 2L;
    private static final long NORMAL_STREAM_DELAY_MS = 6L;
    private static final int MAX_CONTEXT_TURNS = 2;
    private static final int MAX_STORED_TURNS = 20;
    private static final int LOG_PREVIEW_CHARS = 420;
    private static final int LOG_CHUNK_SIZE = 1800;
    private static final boolean ENABLE_VERBOSE_LLM_LOGS = false;

    private ActivityChatBinding binding;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private ExecutorService inferenceExecutor;
    private MainActivity nativeBridge;
    private ModelStateStore stateStore;
    private ModelCatalogLoader catalogLoader;
    private LlmSettingsStore llmSettingsStore;

    private ChatMessageAdapter chatAdapter;
    private final List<ChatTurn> conversationTurns = new ArrayList<>();

    private ModelInfo selectedModel;
    private long modelPtr = 0L;
    private boolean modelLoading = false;
    private boolean generating = false;
    private Runnable streamRunnable;
    private PromptFormatType promptFormatType = PromptFormatType.PLAIN;
    private LlmInferenceSettings currentSettings = LlmInferenceSettings.defaults();
    private String currentSystemRole = PromptTemplateEngine.DEFAULT_SYSTEM_PROMPT;

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                currentSettings = llmSettingsStore.load();
                currentSystemRole = llmSettingsStore.loadSystemRolePrompt();
                Log.i(TAG, "Settings updated maxTokens=" + currentSettings.maxTokens
                        + " temperature=" + currentSettings.temperature
                        + " topK=" + currentSettings.topK
                        + " topP=" + currentSettings.topP
                        + " systemRolePreview=\"" + previewForLog(currentSystemRole) + "\"");
                if (modelPtr != 0L && !modelLoading && !generating) {
                    applySettingsToNativeAsync(currentSettings);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        stateStore = new ModelStateStore(this);
        catalogLoader = new ModelCatalogLoader(this);
        llmSettingsStore = new LlmSettingsStore(this);
        currentSettings = llmSettingsStore.load();
        currentSystemRole = llmSettingsStore.loadSystemRolePrompt();
        nativeBridge = new MainActivity();
        inferenceExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "llama-infer-thread");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        chatAdapter = new ChatMessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvChat.setLayoutManager(layoutManager);
        binding.rvChat.setAdapter(chatAdapter);

        binding.btnStop.setEnabled(false);
        binding.btnStop.setText("Stop (coming soon)");
        binding.btnSettings.setOnClickListener(v ->
                settingsLauncher.launch(new Intent(ChatActivity.this, ChatSettingsActivity.class)));

        selectedModel = resolveSelectedModel();
        if (selectedModel == null) {
            Log.e(TAG, "resolveSelectedModel returned null");
            finish();
            return;
        }
        promptFormatType = PromptTemplateEngine.resolveFormat(selectedModel);
        Log.i(TAG, "Chat opened modelId=" + selectedModel.id
                + " path=" + selectedModel.localPath
                + " format=" + promptFormatType);

        binding.tvModelName.setText(selectedModel.name + " (" + selectedModel.quant + ")");
        binding.tvStatus.setText("Loading model...");
        binding.btnSend.setEnabled(false);

        binding.btnSend.setOnClickListener(v -> runInference());
        binding.etPrompt.setOnEditorActionListener((v, actionId, event) -> {
            if (!generating) {
                runInference();
            }
            return false;
        });

        loadModelInBackground();
    }

    private ModelInfo resolveSelectedModel() {
        String modelId = getIntent().getStringExtra(EXTRA_MODEL_ID);
        if (TextUtils.isEmpty(modelId)) {
            modelId = stateStore.getSelectedModelId();
        }

        if (TextUtils.isEmpty(modelId)) {
            Toast.makeText(this, "No model selected", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No selected model id");
            return null;
        }

        List<ModelInfo> catalog;
        try {
            catalog = catalogLoader.loadModels();
        } catch (IOException | JSONException e) {
            Toast.makeText(this, "Failed to read model catalog", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to read catalog", e);
            return null;
        }

        ModelInfo model = null;
        for (ModelInfo item : catalog) {
            if (TextUtils.equals(item.id, modelId)) {
                model = item;
                break;
            }
        }

        if (model == null) {
            Toast.makeText(this, "Selected model is not in catalog", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Model id not in catalog id=" + modelId);
            return null;
        }

        ModelStateStore.ModelState state = stateStore.readModelState(modelId);
        String localPath = state.localPath;
        if (TextUtils.isEmpty(localPath)) {
            File external = getExternalFilesDir(null);
            if (external != null) {
                localPath = new File(new File(external, "models"), model.filename).getAbsolutePath();
            }
        }

        if (TextUtils.isEmpty(localPath)) {
            Toast.makeText(this, "Model path is missing", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Model path empty id=" + modelId);
            return null;
        }

        File modelFile = new File(localPath);
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            Toast.makeText(this, "Model file not found. Download it first.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Model file missing id=" + modelId + " path=" + localPath);
            return null;
        }

        model.localPath = localPath;
        model.downloaded = true;
        Log.i(TAG, "Resolved model id=" + model.id + " path=" + localPath + " size=" + modelFile.length());
        return model;
    }

    private void loadModelInBackground() {
        modelLoading = true;
        final long startAt = System.currentTimeMillis();
        Log.i(TAG, "Loading model into memory path=" + selectedModel.localPath);
        inferenceExecutor.execute(() -> {
            Log.i(TAG, "loadModelInBackground worker thread=" + Thread.currentThread().getName());
            long ptr;
            try {
                int maxTokens = currentSettings.maxTokens > 0 ? currentSettings.maxTokens : DEFAULT_PREDICT_TOKENS;
                ptr = nativeBridge.createIOLLModel(selectedModel.localPath, maxTokens);
            } catch (Throwable t) {
                ptr = 0L;
                Log.e(TAG, "Native createIOLLModel crashed", t);
            }

            if (ptr != 0L) {
                applySettingsToNativeInternal(ptr, currentSettings);
            }

            final long createdPtr = ptr;
            uiHandler.post(() -> {
                modelLoading = false;
                modelPtr = createdPtr;
                if (createdPtr == 0L) {
                    binding.tvStatus.setText("Model load failed");
                    Toast.makeText(ChatActivity.this, "Failed to initialize model", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Model load failed path=" + selectedModel.localPath
                            + " elapsedMs=" + (System.currentTimeMillis() - startAt));
                    return;
                }
                binding.tvStatus.setText("Model loaded");
                binding.btnSend.setEnabled(true);
                chatAdapter.addMessage(ChatMessage.assistant("Model loaded. Ask your question."));
                scrollChatToBottom();
                Log.i(TAG, "Model loaded into memory ptr=" + createdPtr
                        + " elapsedMs=" + (System.currentTimeMillis() - startAt));
            });
        });
    }

    private void runInference() {
        if (modelLoading || generating || modelPtr == 0L) {
            return;
        }

        String prompt = binding.etPrompt.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) {
            return;
        }

        binding.etPrompt.setText("");

        chatAdapter.addMessage(ChatMessage.user(prompt));
        int assistantMessagePosition = chatAdapter.addMessage(ChatMessage.assistant(""));
        scrollChatToBottom();

        currentSystemRole = llmSettingsStore.loadSystemRolePrompt();
        String formattedPrompt = PromptTemplateEngine.buildPrompt(
                selectedModel,
                prompt,
                conversationTurns,
                MAX_CONTEXT_TURNS,
                currentSystemRole
        );
        Log.i(TAG, "Inference request modelId=" + selectedModel.id
                + " promptFormat=" + promptFormatType
                + " historyTurns=" + conversationTurns.size()
                + " historyTurnsUsed=" + Math.min(MAX_CONTEXT_TURNS, conversationTurns.size())
                + " userPromptChars=" + prompt.length()
                + " formattedChars=" + formattedPrompt.length()
                + " systemRoleChars=" + currentSystemRole.length()
                + " userPromptPreview=\"" + previewForLog(prompt) + "\""
                + " systemRolePreview=\"" + previewForLog(currentSystemRole) + "\""
                + " formattedPromptPreview=\"" + previewForLog(formattedPrompt) + "\"");
        if (ENABLE_VERBOSE_LLM_LOGS) {
            logLargeText("LLM_PROMPT_FULL", formattedPrompt);
        }

        generating = true;
        binding.btnSend.setEnabled(false);
        binding.tvStatus.setText("Generating...");

        final String finalUserPrompt = prompt;
        inferenceExecutor.execute(() -> {
            Log.i(TAG, "runInference worker thread=" + Thread.currentThread().getName());
            currentSettings = llmSettingsStore.load();
            applySettingsToNativeInternal(modelPtr, currentSettings);
            String output;
            final long startedAt = System.currentTimeMillis();
            try {
                output = nativeBridge.runIOLLModel(modelPtr, formattedPrompt);
            } catch (Throwable t) {
                output = "Inference error: " + t.getMessage();
                Log.e(TAG, "runIOLLModel crashed", t);
            }

            String rawOutput = output == null ? "" : output;
            if (ENABLE_VERBOSE_LLM_LOGS) {
                logLargeText("LLM_OUTPUT_RAW_FULL", rawOutput);
            }
            boolean nativeFailure = isNativeFailureMessage(rawOutput);
            String cleaned = nativeFailure
                    ? rawOutput.trim()
                    : PromptTemplateEngine.extractAssistantText(selectedModel, rawOutput, formattedPrompt, finalUserPrompt);
            if (ENABLE_VERBOSE_LLM_LOGS) {
                logLargeText("LLM_OUTPUT_CLEANED_FULL", cleaned);
            }

            Log.i(TAG, "Inference complete modelId=" + selectedModel.id
                    + " elapsedMs=" + (System.currentTimeMillis() - startedAt)
                    + " nativeFailure=" + nativeFailure
                    + " rawChars=" + rawOutput.length()
                    + " cleanedChars=" + cleaned.length());

            final String finalOutput = cleaned;
            uiHandler.post(() -> {
                if (nativeFailure) {
                    binding.tvStatus.setText("Inference failed");
                    chatAdapter.updateMessageText(assistantMessagePosition, finalOutput);
                    binding.btnSend.setEnabled(true);
                    generating = false;
                    scrollChatToBottom();
                    Toast.makeText(ChatActivity.this, "Native inference failed. Check logs.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Native failure response: " + finalOutput);
                    return;
                }
                streamOutput(finalOutput, assistantMessagePosition, finalUserPrompt);
            });
        });
    }

    private void streamOutput(String fullOutput, int assistantMessagePosition, String userPrompt) {
        if (streamRunnable != null) {
            uiHandler.removeCallbacks(streamRunnable);
        }

        final int[] index = {0};
        final int chunkChars = chooseChunkSize(fullOutput.length());
        final long chunkDelayMs = chooseStreamDelay(fullOutput.length());
        final int[] updateCount = {0};

        streamRunnable = new Runnable() {
            @Override
            public void run() {
                if (!generating) {
                    return;
                }

                int end = Math.min(index[0] + chunkChars, fullOutput.length());
                if (end > index[0]) {
                    String chunked = fullOutput.substring(0, end);
                    chatAdapter.updateMessageText(assistantMessagePosition, chunked);
                    index[0] = end;
                    updateCount[0]++;
                    if ((updateCount[0] % 3) == 0 || index[0] >= fullOutput.length()) {
                        scrollChatToBottom();
                    }
                }

                if (index[0] < fullOutput.length()) {
                    uiHandler.postDelayed(this, chunkDelayMs);
                    return;
                }

                generating = false;
                binding.tvStatus.setText("Done");
                binding.btnSend.setEnabled(true);

                conversationTurns.add(new ChatTurn(userPrompt, fullOutput));
                if (conversationTurns.size() > MAX_STORED_TURNS) {
                    conversationTurns.remove(0);
                }
            }
        };

        uiHandler.post(streamRunnable);
    }

    private int chooseChunkSize(int totalChars) {
        if (totalChars <= 0) {
            return MIN_STREAM_CHUNK_CHARS;
        }
        int adaptive = totalChars / 24;
        return Math.max(MIN_STREAM_CHUNK_CHARS, Math.min(MAX_STREAM_CHUNK_CHARS, adaptive));
    }

    private long chooseStreamDelay(int totalChars) {
        return totalChars > 4000 ? FAST_STREAM_DELAY_MS : NORMAL_STREAM_DELAY_MS;
    }

    private void scrollChatToBottom() {
        int count = chatAdapter.getItemCount();
        if (count > 0) {
            binding.rvChat.scrollToPosition(count - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        generating = false;
        if (streamRunnable != null) {
            uiHandler.removeCallbacks(streamRunnable);
        }

        final long ptrToRelease = modelPtr;
        modelPtr = 0L;

        if (inferenceExecutor != null) {
            if (ptrToRelease != 0L) {
                inferenceExecutor.execute(() -> {
                    try {
                        nativeBridge.releaseIOLLModel(ptrToRelease);
                        Log.i(TAG, "Released model ptr=" + ptrToRelease);
                    } catch (Throwable ignore) {
                        Log.e(TAG, "releaseIOLLModel failed", ignore);
                    }
                });
            }
            inferenceExecutor.shutdown();
        }
    }

    private boolean isNativeFailureMessage(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("failed to load model")
                || lower.contains("failed to eval model")
                || lower.contains("model pointer is null")
                || lower.contains("failed to decode model");
    }

    private String previewForLog(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (compact.length() <= LOG_PREVIEW_CHARS) {
            return compact;
        }
        return compact.substring(0, LOG_PREVIEW_CHARS) + "...";
    }

    private void applySettingsToNativeAsync(LlmInferenceSettings settings) {
        final long ptr = modelPtr;
        if (ptr == 0L) {
            return;
        }
        inferenceExecutor.execute(() -> applySettingsToNativeInternal(ptr, settings));
    }

    private void applySettingsToNativeInternal(long ptr, LlmInferenceSettings settings) {
        if (ptr == 0L || settings == null) {
            return;
        }
        try {
            boolean applied = nativeBridge.updateIOLLParams(
                    ptr,
                    settings.maxTokens,
                    settings.temperature,
                    settings.topK,
                    settings.topP);
            Log.i(TAG, "Applied native params ptr=" + ptr
                    + " applied=" + applied
                    + " maxTokens=" + settings.maxTokens
                    + " temperature=" + settings.temperature
                    + " topK=" + settings.topK
                    + " topP=" + settings.topP);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply native params", t);
        }
    }

    private void logLargeText(String label, String text) {
        String safe = text == null ? "" : text;
        int totalLength = safe.length();
        if (totalLength == 0) {
            Log.i(TAG, label + " [empty]");
            return;
        }

        int chunks = (totalLength + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE;
        Log.i(TAG, label + " [start] len=" + totalLength + " chunks=" + chunks);
        for (int i = 0; i < chunks; i++) {
            int start = i * LOG_CHUNK_SIZE;
            int end = Math.min(start + LOG_CHUNK_SIZE, totalLength);
            String part = safe.substring(start, end);
            Log.i(TAG, label + " [part " + (i + 1) + "/" + chunks + "] " + part);
        }
        Log.i(TAG, label + " [end]");
    }
}

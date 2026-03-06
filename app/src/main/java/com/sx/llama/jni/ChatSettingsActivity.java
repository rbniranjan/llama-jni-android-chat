package com.sx.llama.jni;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.sx.llama.jni.databinding.ActivityChatSettingsBinding;

import java.util.Locale;

public class ChatSettingsActivity extends AppCompatActivity {
    private static final int MIN_MAX_TOKENS = 16;
    private static final int MAX_MAX_TOKENS = 4096;
    private static final int MAX_TOKENS_STEP = 16;

    private static final float MIN_TEMP = 0.0f;
    private static final float MAX_TEMP = 2.0f;
    private static final int TEMP_SCALE = 100;

    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 100;

    private static final float MIN_TOP_P = 0.1f;
    private static final float MAX_TOP_P = 1.0f;
    private static final int TOP_P_SCALE = 100;

    private ActivityChatSettingsBinding binding;
    private LlmSettingsStore settingsStore;
    private LlmInferenceSettings initialSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settingsStore = new LlmSettingsStore(this);
        initialSettings = settingsStore.load();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("LLM Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initSliders();
        applySettingsToUi(initialSettings);

        binding.btnSave.setOnClickListener(v -> {
            settingsStore.save(readSettingsFromUi());
            setResult(RESULT_OK);
            finish();
        });

        binding.btnReset.setOnClickListener(v -> {
            applySettingsToUi(LlmInferenceSettings.defaults());
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initSliders() {
        int maxTokenMaxProgress = (MAX_MAX_TOKENS - MIN_MAX_TOKENS) / MAX_TOKENS_STEP;
        binding.seekMaxTokens.setMax(maxTokenMaxProgress);

        int tempMaxProgress = Math.round((MAX_TEMP - MIN_TEMP) * TEMP_SCALE);
        binding.seekTemperature.setMax(tempMaxProgress);

        binding.seekTopK.setMax(MAX_TOP_K - MIN_TOP_K);

        int topPMaxProgress = Math.round((MAX_TOP_P - MIN_TOP_P) * TOP_P_SCALE);
        binding.seekTopP.setMax(topPMaxProgress);

        binding.seekMaxTokens.setOnSeekBarChangeListener(SimpleSeekBarListener.of((seekBar, progress, fromUser) ->
                binding.tvMaxTokensValue.setText(String.valueOf(progressToMaxTokens(progress)))));
        binding.seekTemperature.setOnSeekBarChangeListener(SimpleSeekBarListener.of((seekBar, progress, fromUser) ->
                binding.tvTemperatureValue.setText(formatFloat(progressToTemperature(progress)))));
        binding.seekTopK.setOnSeekBarChangeListener(SimpleSeekBarListener.of((seekBar, progress, fromUser) ->
                binding.tvTopKValue.setText(String.valueOf(progressToTopK(progress)))));
        binding.seekTopP.setOnSeekBarChangeListener(SimpleSeekBarListener.of((seekBar, progress, fromUser) ->
                binding.tvTopPValue.setText(formatFloat(progressToTopP(progress)))));
    }

    private void applySettingsToUi(LlmInferenceSettings settings) {
        LlmInferenceSettings safe = settings.clamp();

        binding.seekMaxTokens.setProgress(maxTokensToProgress(safe.maxTokens));
        binding.tvMaxTokensValue.setText(String.valueOf(safe.maxTokens));

        binding.seekTemperature.setProgress(temperatureToProgress(safe.temperature));
        binding.tvTemperatureValue.setText(formatFloat(safe.temperature));

        binding.seekTopK.setProgress(topKToProgress(safe.topK));
        binding.tvTopKValue.setText(String.valueOf(safe.topK));

        binding.seekTopP.setProgress(topPToProgress(safe.topP));
        binding.tvTopPValue.setText(formatFloat(safe.topP));
    }

    private LlmInferenceSettings readSettingsFromUi() {
        int maxTokens = progressToMaxTokens(binding.seekMaxTokens.getProgress());
        float temperature = progressToTemperature(binding.seekTemperature.getProgress());
        int topK = progressToTopK(binding.seekTopK.getProgress());
        float topP = progressToTopP(binding.seekTopP.getProgress());
        return new LlmInferenceSettings(maxTokens, temperature, topK, topP).clamp();
    }

    private int maxTokensToProgress(int value) {
        int clamped = Math.max(MIN_MAX_TOKENS, Math.min(MAX_MAX_TOKENS, value));
        return (clamped - MIN_MAX_TOKENS) / MAX_TOKENS_STEP;
    }

    private int progressToMaxTokens(int progress) {
        return MIN_MAX_TOKENS + (progress * MAX_TOKENS_STEP);
    }

    private int temperatureToProgress(float value) {
        float clamped = Math.max(MIN_TEMP, Math.min(MAX_TEMP, value));
        return Math.round((clamped - MIN_TEMP) * TEMP_SCALE);
    }

    private float progressToTemperature(int progress) {
        float value = MIN_TEMP + (progress / (float) TEMP_SCALE);
        return Math.max(MIN_TEMP, Math.min(MAX_TEMP, value));
    }

    private int topKToProgress(int value) {
        int clamped = Math.max(MIN_TOP_K, Math.min(MAX_TOP_K, value));
        return clamped - MIN_TOP_K;
    }

    private int progressToTopK(int progress) {
        return MIN_TOP_K + progress;
    }

    private int topPToProgress(float value) {
        float clamped = Math.max(MIN_TOP_P, Math.min(MAX_TOP_P, value));
        return Math.round((clamped - MIN_TOP_P) * TOP_P_SCALE);
    }

    private float progressToTopP(int progress) {
        float value = MIN_TOP_P + (progress / (float) TOP_P_SCALE);
        return Math.max(MIN_TOP_P, Math.min(MAX_TOP_P, value));
    }

    private String formatFloat(float value) {
        return String.format(Locale.US, "%.2f", value);
    }
}

package com.sx.llama.jni;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;

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
    private SystemRolePreset initialRolePreset = SystemRolePreset.GENERAL;
    private boolean suppressRoleSelectionEvent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settingsStore = new LlmSettingsStore(this);
        initialSettings = settingsStore.load();
        initialRolePreset = settingsStore.loadSystemRolePreset();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("LLM Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initRoleControls();
        initSliders();
        applySettingsToUi(initialSettings);
        applyRoleToUi(initialRolePreset, settingsStore.loadSystemRolePrompt());

        binding.btnSave.setOnClickListener(v -> {
            settingsStore.save(readSettingsFromUi());
            settingsStore.saveSystemRole(readRolePresetFromUi(), readRoleTextFromUi());
            setResult(RESULT_OK);
            finish();
        });

        binding.btnReset.setOnClickListener(v -> {
            applySettingsToUi(LlmInferenceSettings.defaults());
            applyRoleToUi(SystemRolePreset.GENERAL, SystemRolePreset.GENERAL.defaultPrompt);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initRoleControls() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SystemRolePreset.labels()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spRolePreset.setAdapter(adapter);
        binding.spRolePreset.setOnItemSelectedListener(SimpleItemSelectedListener.of(position -> {
            if (suppressRoleSelectionEvent) {
                return;
            }
            SystemRolePreset preset = presetFromPosition(position);
            if (preset != SystemRolePreset.CUSTOM) {
                binding.etSystemRole.setText(preset.defaultPrompt);
            }
        }));
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

    private void applyRoleToUi(SystemRolePreset preset, String roleText) {
        SystemRolePreset safePreset = preset == null ? SystemRolePreset.GENERAL : preset;
        suppressRoleSelectionEvent = true;
        binding.spRolePreset.setSelection(safePreset.ordinal(), false);
        suppressRoleSelectionEvent = false;

        if (!TextUtils.isEmpty(roleText)) {
            binding.etSystemRole.setText(roleText);
        } else if (safePreset == SystemRolePreset.CUSTOM) {
            binding.etSystemRole.setText("");
        } else {
            binding.etSystemRole.setText(safePreset.defaultPrompt);
        }
    }

    private SystemRolePreset readRolePresetFromUi() {
        return presetFromPosition(binding.spRolePreset.getSelectedItemPosition());
    }

    private String readRoleTextFromUi() {
        String text = binding.etSystemRole.getText() == null ? "" : binding.etSystemRole.getText().toString().trim();
        if (!TextUtils.isEmpty(text)) {
            return text;
        }

        SystemRolePreset preset = readRolePresetFromUi();
        if (preset == SystemRolePreset.CUSTOM) {
            return PromptTemplateEngine.DEFAULT_SYSTEM_PROMPT;
        }
        return preset.defaultPrompt;
    }

    private SystemRolePreset presetFromPosition(int position) {
        SystemRolePreset[] presets = SystemRolePreset.values();
        if (position < 0 || position >= presets.length) {
            return SystemRolePreset.GENERAL;
        }
        return presets[position];
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

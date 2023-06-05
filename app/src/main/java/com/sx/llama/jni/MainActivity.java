package com.sx.llama.jni;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.sx.llama.jni.databinding.ActivityMainBinding;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'jni' library on application startup.
    static {
        System.loadLibrary("jni");
        System.loadLibrary("io-prompt");
        System.loadLibrary("interactive");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;

        // System.out.println(String.format("%s/%s", getFilesDir().getParent(), modelName));
        System.out.println(String.format("%s/%s", getExternalFilesDir(null).getParent(), modelName));

        llamaIOPrompt(tv, "Please tell me the largest city in China.");
        //llamaInteractive(tv, "Please tell me the largest city in China.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放 C++ 层的 Model 实例
        releaseIOLLModel(modelPtr);
        //releaseLLModel(modelPtr);
    }

    /**
     * A native method that is implemented by the 'jni' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    private final String modelName = "ggml-vic7b-q5_0.bin";

    private final String txtName = "chat-with-bob.txt";

    private long modelPtr;

    private void llamaIOPrompt(TextView tv, String userPrompt) {
        modelPtr = createIOLLModel(String.format("%s/%s", getExternalFilesDir(null).getParent(), modelName), 256);
        String output = runIOLLModel(modelPtr, userPrompt);
        tv.setText(output);
        releaseIOLLModel(modelPtr);
    }

    private native long createIOLLModel(String modelPath, int llSize);

    private native String runIOLLModel(long modelPtr, String llPrompt);

    private native void releaseIOLLModel(long modelPtr);

    private void llamaInteractive(TextView tv, String userPrompt) {
        modelPtr = createLLModel(String.format("%s/%s", getExternalFilesDir(null).getParent(), modelName), 256);
        initLLModel(modelPtr, String.format("%s/%s", getExternalFilesDir(null).getParent(), txtName), userPrompt);
        while (whileLLModel(modelPtr)) {
            int[] tokenList = embdLLModel(modelPtr);
            if (printLLModel(modelPtr)) {
                for (int t : tokenList) {
                    System.out.println(new String(textLLModel(modelPtr, t), StandardCharsets.UTF_8));
                }
            }
            if (breakLLModel(modelPtr)) {
                System.out.println("break");
                break;
            }
        }
        tv.setText(stringFromJNI());
        releaseLLModel(modelPtr);
    }

    private native long createLLModel(String modelPath, int llSize);

    private native void initLLModel(long modelPtr, String promptPath, String llPrompt);

    private native boolean whileLLModel(long modelPtr);

    private native boolean breakLLModel(long modelPtr);

    private native boolean printLLModel(long modelPtr);

    private native int[] embdLLModel(long modelPtr);

    private native byte[] textLLModel(long modelPtr, int modelToken);

    private native void releaseLLModel(long modelPtr);

}

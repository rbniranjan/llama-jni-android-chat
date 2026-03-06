package com.sx.llama.jni;

public class MainActivity {
    static {
        System.loadLibrary("jni");
        System.loadLibrary("io-prompt");
        System.loadLibrary("interactive");
    }

    public native String stringFromJNI();

    public native long createIOLLModel(String modelPath, int llSize);

    public native String runIOLLModel(long modelPtr, String llPrompt);

    public native boolean updateIOLLParams(long modelPtr, int maxTokens, float temperature, int topK, float topP);

    public native void releaseIOLLModel(long modelPtr);

    public native long createLLModel(String modelPath, int llSize);

    public native void initLLModel(long modelPtr, String promptPath, String llPrompt);

    public native boolean whileLLModel(long modelPtr);

    public native boolean breakLLModel(long modelPtr);

    public native boolean printLLModel(long modelPtr);

    public native int[] embdLLModel(long modelPtr);

    public native byte[] textLLModel(long modelPtr, int modelToken);

    public native void releaseLLModel(long modelPtr);
}

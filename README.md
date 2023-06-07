# llama-jni

English / [简体中文](./README-zh.md)

Android JNI for port of Facebook's LLaMA model in C/C++

Scenario:

- Input modules

- Voice control modules

## Table of Contents

- [Background](#background)

- [Install](#install)

- [Usage](#usage)

- [Examples](#examples)

- [Related Efforts](#related-efforts)

- [Maintainers](#maintainers)

- [Contributing](#contributing)

- [License](#license)

## Background

[**llama.cpp**](https://github.com/ggerganov/llama.cpp) uses pure `C/C++` language to provide the port of [**LLaMA**](https://arxiv.org/abs/2302.13971), and implements the operation of [**LLaMA**](https://github.com/facebookresearch/llama) in MacBook and Android devices through 4-bit quantization.

In order to better support the localization operation of large language models (LLM) on mobile devices, `llama-jni` aims to further encapsulate [**llama.cpp**](https://github.com/ggerganov/llama.cpp) and provide several common functions before the `C/C++` code is compiled for subsequent direct calls by the engineering to help mobile applications on Android devices directly use the LLM stored locally.

The locally run `llama-jni` can empower mobile devices with powerful AI capabilities without network connection, which maximizes privacy and security.

The goals of `llama-jni` include:

1. Refactoring of the code for [main.cpp](https://github.com/ggerganov/llama.cpp/blob/master/examples/main/main.cpp) in [**llama.cpp**](https://github.com/ggerganov/llama.cpp) to achieve text output equivalent to the system command line in `Android` projects.
2. Introduction of logs for `C/C++` code to better observe program operation in logs during debugging of `Android` projects.
3. Rewriting of several `CMakeLists.txt` files to achieve smooth compilation process in `Android Studio`.
4. Typical project structure and usage examples for `Native C++` projects in `Android Studio`, where running `MainActivity.java` can observe the inference effects of the LLM in logs.
5. Equal support for multiple models, input parameters, and prompt mode options in [**llama.cpp**](https://github.com/ggerganov/llama.cpp).

## Install

The tool configuration information of `llama-jni` is as follows, which requires support for [NDK and CMake](https://developer.android.google.cn/studio/projects/install-ndk?hl=zh-cn#default-version). Please make sure they have been installed locally.

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.sx.llama.jni'
    compileSdk 33

    defaultConfig {
        applicationId "com.sx.llama.jni"
        minSdk 24
        targetSdk 33
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }
    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

## Usage

### Preparations

`llama-jni` does not include the language model, please prepare the LLM by yourself, and they need to be supported by the [specified version](https://github.com/ggerganov/llama.cpp/releases/tag/master-7e4ea5b) of [**llama.cpp**](https://github.com/ggerganov/llama.cpp).

On the mobile application dedicated folder of Android external storage device, you need to store the necessary LLM (e.g. [GPT4All](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/cpp/README.md#using-gpt4all)) and Prompt text files (e.g. [chat-with-bob.txt](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/cpp/prompts/chat-with-bob.txt)). Assuming their paths are:

```sh
/storage/emulated/0/Android/data/com.sx.llama.jni/ggml-vic7b-q5_0.bin
/storage/emulated/0/Android/data/com.sx.llama.jni/chat-with-bob.txt
```

Then the following two pieces of code in [MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java) need to correspond to their file names:

```java
private final String modelName = "ggml-vic7b-q5_0.bin";
private final String txtName = "chat-with-bob.txt";
```

After the above work is completed, call the `llamaInteractive` function in [MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java), and modify the second parameter to the dialog input required by the user to complete the preparation work before the LLM inference.

```java
llamaInteractive(tv, "Please tell me the largest city in China.");
```

### Run

Select AVD in `Android Studio` and then click the **Run** icon <img src="https://developer.android.google.cn/static/studio/images/buttons/toolbar-run.png?hl=zh-cn" class="inline-icon" alt=""> to execute `llama-jni` based on the local model file.

<img src="https://github.com/shixiangcap/llama-jni/assets/41248645/be3c8154-d117-46d2-8301-6bb19ea370ed"/>

### Arguments

`llama-jni` provides two refactoring methods for the [main.cpp](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/cpp/examples/main/main.cpp), which are `single complete return` and `continuous stream printing` in the `Android` project.

For these two modes, [MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java) displays typical encapsulation and call methods:

- Single complete return

```java
// call
llamaIOPrompt(tv, "Please tell me the largest city in China.");

// encapsulation
private void llamaIOPrompt(TextView tv, String userPrompt) {
    modelPtr = createIOLLModel(String.format("%s/%s", getExternalFilesDir(null).getParent(), modelName), 256);
    String output = runIOLLModel(modelPtr, userPrompt);
    tv.setText(output);
    releaseIOLLModel(modelPtr);
}
```

The equivalent [**llama.cpp**](https://github.com/ggerganov/llama.cpp) command for this mode is

```sh
./main -m "/storage/emulated/0/Android/data/com.sx.llama.jni/ggml-vic7b-q5_0.bin" -p "Please tell me the largest city in China." -n 256
```

- Continuous stream printing

```java
// call
llamaInteractive(tv, "Please tell me the largest city in China.");

// encapsulation
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
```

The equivalent [**llama.cpp**](https://github.com/ggerganov/llama.cpp) command for this mode (some parameters are not exposed to [MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)) is

```sh
./main -m "/storage/emulated/0/Android/data/com.sx.llama.jni/ggml-vic7b-q5_0.bin" -n 256 --repeat_penalty 1.0 --color -i -r "User:" -f "/storage/emulated/0/Android/data/com.sx.llama.jni/chat-with-bob.txt"
```

## Examples

### Single Complete Return

After running successfully, `llama-jni` can display the complete inference result of the LLM on the simulator interface based on the following code segment in [MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java).

```java
tv.setText(output);
```

<img src="https://github.com/shixiangcap/llama-jni/assets/41248645/3174ec36-da68-466f-a0b0-6c3fef9edb0c" width=50%/>

### Continuous Stream Printing

After running successfully, `llama-jni` can continuously print every `token` inference result of the large language model in the log column of `Android Studio` based on the following code segment in [MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java).

```java
for (int t : tokenList) {
    System.out.println(new String(textLLModel(modelPtr, t), StandardCharsets.UTF_8));
}
```

https://github.com/shixiangcap/llama-jni/assets/41248645/f9405141-e994-409d-8d20-94eef29025f3

## Related Efforts

- [LLaMA](https://github.com/facebookresearch/llama) — Inference code for LLaMA models.
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — Port of Facebook's LLaMA model in C/C++.

## Maintainers

[@shixiangcap](https://github.com/shixiangcap)

## Contributing

Feel free to dive in! [Open an issue](https://github.com/shixiangcap/llama-jni/issues) or submit PRs.

### Contributors

This project exists thanks to all the people who contribute.
<a href="https://github.com/orgs/shixiangcap/people"><img src="https://avatars.githubusercontent.com/u/134358037" height=20rem/></a>

## License

[MIT](LICENSE) © shixiangcap

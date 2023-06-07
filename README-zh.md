# llama-jni

简体中文 / [English](./README.md)

Android JNI for port of Facebook's LLaMA model in C/C++

适用场景：

- 输入类模块

- 语音控制类模块

## 内容列表

- [背景](#背景)

- [安装](#安装)

- [使用说明](#使用说明)

- [示例](#示例)

- [相关仓库](#相关仓库)

- [维护者](#维护者)

- [如何贡献](#如何贡献)

- [使用许可](#使用许可)

## 背景

[**llama.cpp**](https://github.com/ggerganov/llama.cpp)使用纯`C/C++`语言，提供了[**LLaMA模型**](https://arxiv.org/abs/2302.13971)的接口，并且使用4位整数量化在MacBook和Android设备上实现了[**LLaMA模型**](https://github.com/facebookresearch/llama)的运行。

为了更好地支持大型语言模型在移动设备上的本地化运行，`llama-jni`旨在实现[**llama.cpp**](https://github.com/ggerganov/llama.cpp)的进一步封装，于`C/C++`代码编译之前提供若干常用函数，以备后续工程的直接调用，帮助Android设备上的移动应用程序直接使用存储在本地的模型文件。

本地运行的`llama-jni`无需联网即可赋予移动设备强大的AI能力，最大限度地保证了隐私性和安全性。

`llama-jni`的目标包括：

1. 针对[**llama.cpp**](https://github.com/ggerganov/llama.cpp)中[main函数](https://github.com/ggerganov/llama.cpp/blob/master/examples/main/main.cpp)的代码重构，以便在`Android`项目中实现与系统命令行同等效果的文字输出。
2. 针对`C/C++`代码的日志引入，以便在调试`Android`项目时更好地在日志中观察程序的运行。
3. 针对若干`CMakeLists.txt`的重新编写，以便在`Android Studio`中实现顺畅的编译流程。
4. 针对`Android Studio`中`Native C++`项目的典型项目结构和使用示例，运行`MainActivity.java`即可于日志中观察大型语言模型的推理效果。
5. 针对[**llama.cpp**](https://github.com/ggerganov/llama.cpp)中多种模型、输入参数、Prompt模式等选项的同等支持。

## 安装

`llama-jni`的工具配置信息如下，其同时需要[NDK 和 CMake](https://developer.android.google.cn/studio/projects/install-ndk?hl=zh-cn#default-version)的支持。请确保本地已经安装了它们。

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

## 使用说明

### 准备工作

`llama-jni`并不包含模型文件，请自行准备大型语言模型文件，且其需要得到[**llama.cpp**](https://github.com/ggerganov/llama.cpp)中[指定版本](https://github.com/ggerganov/llama.cpp/releases/tag/master-7e4ea5b)的支持。

Android外部存储设备上的移动应用程序专用文件夹中，需要存储必要的大型语言模型文件（如：[GPT4All](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/cpp/README.md#using-gpt4all)）和Prompt文本文件（如：[chat-with-bob.txt](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/cpp/prompts/chat-with-bob.txt)），假设其路径为：

```sh
/storage/emulated/0/Android/data/com.sx.llama.jni/ggml-vic7b-q5_0.bin
/storage/emulated/0/Android/data/com.sx.llama.jni/chat-with-bob.txt
```

则[MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)中的以下两段代码需要与它们的文件名对应：

```java
private final String modelName = "ggml-vic7b-q5_0.bin";
private final String txtName = "chat-with-bob.txt";
```

上述工作完成后，调用[MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)中的`llamaInteractive`函数，并将第二个参数修改为用户所需的对话输入，即完成了大型语言模型推理前的准备工作。

```java
llamaInteractive(tv, "Please tell me the largest city in China.");
```

### 运行

在`Android Studio`中选择 AVD，然后点击 **Run** 图标 <img src="https://developer.android.google.cn/static/studio/images/buttons/toolbar-run.png?hl=zh-cn" class="inline-icon" alt="">，`llama-jni`即会执行基于本地模型文件的推理。

<img src="https://github.com/shixiangcap/llama-jni/assets/41248645/be3c8154-d117-46d2-8301-6bb19ea370ed"/>

### 参数选择

`llama-jni`提供了[main函数](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/cpp/examples/main/main.cpp)的两种重构方式，分别为`Android`项目中的`单次完整返回`和`持续流式打印`。

针对这两种模式，[MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)中都展示了典型的封装与调用方法：

- 单次完整返回

```java
// 调用
llamaIOPrompt(tv, "Please tell me the largest city in China.");

// 封装
private void llamaIOPrompt(TextView tv, String userPrompt) {
    modelPtr = createIOLLModel(String.format("%s/%s", getExternalFilesDir(null).getParent(), modelName), 256);
    String output = runIOLLModel(modelPtr, userPrompt);
    tv.setText(output);
    releaseIOLLModel(modelPtr);
}
```

该模式等价的[**llama.cpp**](https://github.com/ggerganov/llama.cpp)命令为：

```sh
./main -m "/storage/emulated/0/Android/data/com.sx.llama.jni/ggml-vic7b-q5_0.bin" -p "Please tell me the largest city in China." -n 256
```

- 持续流式打印

```java
// 调用
llamaInteractive(tv, "Please tell me the largest city in China.");

// 封装
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

该模式等价的[**llama.cpp**](https://github.com/ggerganov/llama.cpp)命令为（部分参数并未向[MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)暴露）：

```sh
./main -m "/storage/emulated/0/Android/data/com.sx.llama.jni/ggml-vic7b-q5_0.bin" -n 256 --repeat_penalty 1.0 --color -i -r "User:" -f "/storage/emulated/0/Android/data/com.sx.llama.jni/chat-with-bob.txt"
```

## 示例

### 单次完整返回模式

成功运行后，基于[MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)中的如下一段代码，`llama-jni`即可在模拟器界面上显示大型语言模型完整的推理结果。

```java
tv.setText(output);
```

<img src="https://github.com/shixiangcap/llama-jni/assets/41248645/3174ec36-da68-466f-a0b0-6c3fef9edb0c" width=50%/>

### 持续流式打印

成功运行后，基于[MainActivity.java](https://github.com/shixiangcap/llama-jni/blob/master/app/src/main/java/com/sx/llama/jni/MainActivity.java)中的如下一段代码，`llama-jni`即可在`Android Studio`的日志栏持续打印大型语言模型推理出的每一个`token`。

```java
for (int t : tokenList) {
    System.out.println(new String(textLLModel(modelPtr, t), StandardCharsets.UTF_8));
}
```

https://github.com/shixiangcap/llama-jni/assets/41248645/f9405141-e994-409d-8d20-94eef29025f3

## 相关仓库

- [LLaMA](https://github.com/facebookresearch/llama) — LLaMA模型的推理代码。
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — 基于C/C++的Facebook LLaMA模型接口。

## 维护者

[@shixiangcap](https://github.com/shixiangcap)

## 如何贡献

非常欢迎你的加入！[提一个Issue](https://github.com/shixiangcap/llama-jni/issues)或者提交一个Pull Request。

### 贡献者

感谢以下参与项目的人：
<a href="https://github.com/orgs/shixiangcap/people"><img src="https://avatars.githubusercontent.com/u/134358037" height=20rem/></a>

## 使用许可

[MIT](LICENSE) © shixiangcap

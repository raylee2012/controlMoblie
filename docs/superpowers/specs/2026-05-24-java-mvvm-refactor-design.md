# Java + ViewBinding + XML + MVVM 重构设计文档

## 背景

当前 ControlMoblie 使用 Kotlin + Jetpack Compose 实现。现需完整重构为：
- **Java** 作为开发语言（所有 `.kt` → `.java`）
- **ViewBinding + XML Layout** 替代 Jetpack Compose
- **MVVM + LiveData** 作为 UI 架构模式
- 功能完全保留（语音控制、Accessibility 执行、OCR 截图、ML Kit、WeChat scheme 绕过等）

---

## 目标

1. 所有源码文件转为 `.java`（包括 Service、Activity、数据模型、工具类）
2. UI 层使用 XML Layout + ViewBinding
3. 状态管理使用 `LiveData` + `ViewModel`
4. Service 与 View 层通过 Binder + LiveData 通信，无直接引用
5. 内存安全：避免 `observeForever` 泄漏，ServiceConnection 正确解绑

---

## 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         View Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ MainActivity │  │ OverlayView  │  │  StatusBar   │          │
│  │ (XML+Binding)│  │ (WindowMgr)  │  │  (Notification)│        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
└───────┬─┴─────────────────┬─┴─────────────────┬─┴────────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ViewModel Layer                            │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │
│  │ MainViewModel  │  │OverlayPresenter│  │VoiceStatusVM   │    │
│  │ (AndroidViewModel)││(非 Lifecycle)  │  │(派生状态转换)  │    │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘    │
└────────┬┴────────────────────┬┴───────────────────┬┴────────────┘
         │                     │                     │
         │           ┌─────────┴──────────┐         │
         │           │  ServiceConnection   │         │
         │           │  (bind/unbind)     │         │
         │           └─────────┬──────────┘         │
         ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Service Layer                              │
│  ┌─────────────────────┐  ┌─────────────────────┐              │
│  │ VoiceControlService │  │ ControlAccessibility│              │
│  │  (前台 Service)      │  │   Service           │              │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │              │
│  │  │VoiceControl   │  │  │  │Accessibility  │  │              │
│  │  │Binder         │  │  │  │Binder         │  │              │
│  │  │MutableLiveData│  │  │  │MutableLiveData│  │              │
│  │  └───────────────┘  │  │  └───────────────┘  │              │
│  └─────────────────────┘  └─────────────────────┘              │
└─────────────────────────────────────────────────────────────────┘
         │                     │
         ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Business Layer                             │
│  ASR(SherpaOnnx)  TTS(SherpaOnnx)  LLM/Parser  OCR(MLKit)     │
│  ExecutionEngine   ScreenCaptureManager   AppResolver          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 包结构

```
com.controlmoblie
├── ControlMoblieApp.java
├── model/
│   ├── Action.java                    ← sealed class → 抽象基类+静态内部类
│   ├── InstructionResult.java
│   └── ScreenState.java
├── service/
│   ├── VoiceControlService.java
│   ├── ControlAccessibilityService.java
│   └── binder/
│       ├── VoiceControlBinder.java
│       └── AccessibilityBinder.java
├── asr/
│   ├── SpeechRecognizerManager.java
│   └── SenseVoiceModelManager.java
├── tts/
│   ├── TtsSpeaker.java
│   └── TtsModelManager.java
├── llm/
│   ├── LlmEngine.java
│   ├── InstructionParser.java
│   ├── CommandTemplates.java
│   └── NativeLlmEngine.java
├── execution/
│   └── ExecutionEngine.java
├── ocr/
│   ├── ScreenOcr.java
│   └── ScreenCaptureManager.java
├── resolver/
│   └── AppResolver.java
├── util/
│   ├── ScreenReader.java
│   └── PermissionHelper.java
├── ui/
│   ├── main/
│   │   ├── MainActivity.java
│   │   ├── MainViewModel.java
│   │   └── MainViewModelFactory.java
│   └── overlay/
│       ├── OverlayService.java
│       └── OverlayPresenter.java
├── viewmodel/
│   ├── VoiceState.java              ← POJO，LiveData 用的数据载体
│   ├── DownloadProgress.java
│   └── OcrState.java
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── overlay_control.xml
    │   └── item_permission.xml
    └── values/
        ├── strings.xml
        ├── themes.xml
        └── ids.xml
```

---

## Service ↔ ViewModel 通信设计

### 核心原则

- **同一个进程内**：Service 和 Activity 在同一进程，使用普通 `Binder`（非 AIDL）
- **无直接引用**：Activity 不直接引用 Service 实例，只通过 Binder 获取 LiveData
- **内存安全**：`ViewModel.onCleared()` 时解绑 ServiceConnection，移除所有 Observer

### VoiceControlBinder

```java
public class VoiceControlBinder extends Binder {
    private final MutableLiveData<VoiceState> voiceState = new MutableLiveData<>();
    private final MutableLiveData<String> recognizedText = new MutableLiveData<>();
    private final MutableLiveData<InstructionResult> lastResult = new MutableLiveData<>();
    
    // Service 内部更新状态
    public void setRecording(boolean recording) {
        VoiceState current = voiceState.getValue();
        if (current == null) current = new VoiceState();
        current.setRecording(recording);
        voiceState.postValue(current);
    }
    
    public void setRecognizedText(String text) {
        recognizedText.postValue(text);
    }
    
    public void setResult(InstructionResult result) {
        lastResult.postValue(result);
    }
    
    // ViewModel 观察
    public MutableLiveData<VoiceState> getVoiceState() { return voiceState; }
    public MutableLiveData<String> getRecognizedText() { return recognizedText; }
    public MutableLiveData<InstructionResult> getLastResult() { return lastResult; }
}
```

### MainViewModel 中的 Service 绑定

```java
public class MainViewModel extends AndroidViewModel {
    private final MutableLiveData<VoiceState> voiceState = new MutableLiveData<>();
    private final MutableLiveData<String> recognizedText = new MutableLiveData<>();
    private VoiceControlBinder binder;
    private final List<Observer<?>> observers = new ArrayList<>();
    
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VoiceControlBinder) service;
            
            Observer<VoiceState> voiceObserver = v -> voiceState.postValue(v);
            Observer<String> textObserver = t -> recognizedText.postValue(t);
            
            binder.getVoiceState().observeForever(voiceObserver);
            binder.getRecognizedText().observeForever(textObserver);
            
            observers.add(voiceObserver);
            observers.add(textObserver);
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };
    
    public void bindService(Context context) {
        Intent intent = new Intent(context, VoiceControlService.class);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }
    
    public void unbindService(Context context) {
        if (binder != null) {
            // 移除所有 Observer，防止内存泄漏
            for (Observer<?> observer : observers) {
                binder.getVoiceState().removeObserver((Observer<VoiceState>) observer);
            }
            observers.clear();
            context.unbindService(connection);
            binder = null;
        }
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        unbindService(getApplication());
    }
    
    public LiveData<VoiceState> getVoiceState() { return voiceState; }
    public LiveData<String> getRecognizedText() { return recognizedText; }
}
```

### AccessibilityBinder

同理，`ControlAccessibilityService` 暴露 `AccessibilityBinder`，包含：
- `MutableLiveData<ScreenState> screenState` — 当前屏幕文本节点状态
- `MutableLiveData<InstructionResult> actionResult` — 最近一次执行结果

---

## View 层 XML 布局

### activity_main.xml

根布局为垂直 `LinearLayout`，包含：
1. **标题**：`TextView "Control Mobile"`
2. **权限项**（3 个，复用 `item_permission.xml`）：
   - 悬浮窗权限
   - 录音权限
   - 无障碍服务
3. **ASR 模型下载区**（条件显示）：
   - 下载中：`ProgressBar + TextView`
   - 未下载：`Button "下载语音识别模型"`
   - 已就绪：`TextView "语音识别 ✓"`
4. **TTS 模型下载区**（条件显示）：同上结构
5. **OCR 初始化区**（条件显示）：
   - 未初始化：`Button "初始化 OCR 识别"`
   - 已就绪：`TextView "OCR识别 ✓"`
6. **启动按钮**：`Button "启动语音控制"`

所有条件显示/隐藏通过 `MainViewModel` 的 `LiveData<Boolean>` 控制，Activity 中 `observe` 后调用 `setVisibility(View.VISIBLE/GONE)`。

### overlay_control.xml

悬浮窗根布局为 `FrameLayout`，包含：
- `TextView` — 状态文字（如"🎤 录音中..."）
- `ProgressBar` — 可选，表示处理中

悬浮窗由 `OverlayService` 通过 `WindowManager.addView()` 创建和更新，不经过 Activity。

---

## ViewModel 类设计

### MainViewModel

| 状态 | 类型 | 说明 |
|------|------|------|
| `hasOverlay` | `LiveData<Boolean>` | 悬浮窗权限 |
| `hasAudio` | `LiveData<Boolean>` | 录音权限 |
| `hasAccessibility` | `LiveData<Boolean>` | 无障碍服务权限 |
| `asrModelReady` | `LiveData<Boolean>` | ASR 模型就绪 |
| `ttsModelReady` | `LiveData<Boolean>` | TTS 模型就绪 |
| `ocrReady` | `LiveData<Boolean>` | OCR 初始化完成 |
| `asrProgress` | `LiveData<DownloadProgress>` | ASR 下载进度 |
| `ttsProgress` | `LiveData<DownloadProgress>` | TTS 下载进度 |
| `voiceState` | `LiveData<VoiceState>` | 语音服务当前状态 |
| `lastRecognizedText` | `LiveData<String>` | 最近一次识别文本 |
| `lastResult` | `LiveData<InstructionResult>` | 最近一次执行结果 |

### OverlayPresenter

**注意**：悬浮窗没有 Activity 生命周期，因此不使用 `ViewModel`（没有 `onCleared()`）。

改用自定义 `OverlayPresenter`，在 `OverlayService.onCreate()` 中创建，在 `onDestroy()` 中手动调用 `detach()` 释放：

```java
public class OverlayPresenter {
    private final MutableLiveData<String> statusText = new MutableLiveData<>();
    private VoiceControlBinder binder;
    private final List<Observer<?>> observers = new ArrayList<>();
    
    public void attach(Service service, VoiceControlBinder binder) {
        this.binder = binder;
        Observer<VoiceState> observer = state -> {
            statusText.postValue(formatStatus(state));
            updateOverlayView(service, state);
        };
        binder.getVoiceState().observeForever(observer);
        observers.add(observer);
    }
    
    public void detach() {
        if (binder != null) {
            for (Observer<?> o : observers) {
                binder.getVoiceState().removeObserver((Observer<VoiceState>) o);
            }
            observers.clear();
            binder = null;
        }
    }
}
```

---

## 数据模型设计（Kotlin → Java）

### Kotlin sealed class → Java 抽象基类

当前 `Action.kt`：
```kotlin
sealed class Action {
    data class Click(val target: String) : Action()
    data class Scroll(val direction: ScrollDirection, val distance: ScrollDistance) : Action()
    data class OpenApp(val package: String, val displayName: String = "") : Action()
    // ...
}
```

重构后 `Action.java`：
```java
public abstract class Action {
    public enum Type { CLICK, SCROLL, OPEN_APP, OPEN_WECHAT_PAGE, NAVIGATE, SEQUENCE }
    public abstract Type getType();
    
    public static class Click extends Action {
        private final String target;
        public Click(String target) { this.target = target; }
        @Override public Type getType() { return Type.CLICK; }
        public String getTarget() { return target; }
    }
    
    public static class Scroll extends Action {
        private final ScrollDirection direction;
        private final ScrollDistance distance;
        public Scroll(ScrollDirection direction, ScrollDistance distance) {
            this.direction = direction;
            this.distance = distance;
        }
        @Override public Type getType() { return Type.SCROLL; }
        public ScrollDirection getDirection() { return direction; }
        public ScrollDistance getDistance() { return distance; }
    }
    
    // ... 其他子类同理
}
```

### 其他数据类

- `InstructionResult` — POJO，包含 `success`, `message`, `action`
- `ScreenState` — POJO，包含 `packageName`, `texts`（`List<String>`）
- `VoiceState` — POJO，包含 `recording`, `processing`, `executing`
- `DownloadProgress` — POJO，包含 `progress`（0.0~1.0），`-1` 表示未开始

---

## Build 配置变更

### app/build.gradle.kts

**移除**：
```kotlin
buildFeatures {
    compose = true
}
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
}
```

**添加**：
```kotlin
buildFeatures {
    viewBinding = true
}
```

**依赖变更**：

| 移除 | 添加 |
|------|------|
| `platform(libs.compose.bom)` | `androidx.lifecycle:lifecycle-livedata:2.7.0` |
| `libs.compose.ui` | `androidx.lifecycle:lifecycle-viewmodel:2.7.0` |
| `libs.compose.material3` | `androidx.lifecycle:lifecycle-extensions:2.2.0` |
| `libs.activity.compose` | `androidx.activity:activity:1.8.2` |
| `libs.compose.ui.tooling` | `com.google.android.material:material:1.11.0` |

**保留不变**：
- `lifecycle-runtime-ktx`, `lifecycle-runtime`, `lifecycle-service`
- `coroutines-android`
- `sherpa-onnx` AAR, `onnxruntime-android`
- `mlkit-text-recognition-chinese`
- `commons-compress`

---

## 内存泄漏防护

### 1. ViewModel.onCleared()

`MainViewModel` 重写 `onCleared()`，调用 `unbindService()`，确保 Activity 销毁时 ServiceConnection 被解绑。

### 2. Observer 移除

所有 `observeForever()` 添加的 Observer 都保存在 `List<Observer<?>>` 中，解绑时逐个 `removeObserver()`。

### 3. OverlayPresenter.detach()

`OverlayService.onDestroy()` 中必须调用 `overlayPresenter.detach()`，移除所有 Observer。

### 4. AccessibilityBinder 同理

如果 `MainActivity` 绑定了 `AccessibilityBinder`，在 `onCleared()` 中一并解绑。

---

## 功能保留清单

| 功能模块 | 当前实现 | 重构后 | 状态 |
|---------|---------|--------|------|
| 语音识别（ASR） | `SpeechRecognizerManager.kt` + `SenseVoiceModelManager.kt` | Java 同名类 | ✅ 保留 |
| 语音合成（TTS） | `TtsSpeaker.kt` + `TtsModelManager.kt` | Java 同名类 | ✅ 保留 |
| LLM 推理 | `LlmEngine.kt` + `NativeLlmEngine.kt` | Java 同名类 | ✅ 保留 |
| 指令解析 | `InstructionParser.kt` + `CommandTemplates.kt` | Java 同名类 | ✅ 保留 |
| Accessibility 执行 | `ControlAccessibilityService.kt` | Java 同名类 | ✅ 保留 |
| OCR 截图识别 | `ScreenOcr.kt` + `ScreenCaptureManager.kt` | Java 同名类，移至 `ocr/` 包 | ✅ 保留 |
| WeChat scheme 绕过 | `wechatPageSchemes` map | Java `Map` 常量 | ✅ 保留 |
| 全应用包名解析 | `AppResolver.kt`（78 个应用） | Java 同名类，移至 `resolver/` 包 | ✅ 保留 |
| 悬浮窗 | `ControlOverlay.kt`（Compose） | `OverlayService` + `overlay_control.xml` + `OverlayPresenter` | ✅ 保留 |
| 语音控制前台 Service | `VoiceControlService.kt` | Java 同名类 + `VoiceControlBinder` | ✅ 保留 |
| 主页 UI | `MainActivity.kt`（Compose） | `MainActivity.java` + `activity_main.xml` + `MainViewModel` | ✅ 保留 |
| 屏幕文本读取 | `ScreenReader.kt` | Java 同名类 | ✅ 保留 |
| 权限辅助 | `PermissionHelper.kt` | Java 同名类 | ✅ 保留 |
| MediaProjection 截图 | `ScreenCaptureManager.kt` | Java 同名类 | ✅ 保留 |
| ML Kit OCR | `ScreenOcr.kt` | Java 同名类 | ✅ 保留 |

---

## 风险与回退方案

1. **Java 不支持 Kotlin 协程的 suspend 函数**：
   - `ScreenOcr.recognize()` 当前是 `suspend` 函数
   - 重构后改为回调接口：`void recognize(Bitmap bitmap, OnOcrResultCallback callback)`
   - 内部仍可用 `Executors` 或 `HandlerThread` 执行异步任务

2. **Jetpack Compose → XML 的 UI 表现力差异**：
   - Compose 的条件渲染非常灵活，XML 需要多个 `setVisibility()` 调用
   - 对功能无影响，只是代码量增加

3. **LiveData 的线程安全**：
   - `postValue()` 用于后台线程，`setValue()` 用于主线程
   - Service 中更新状态统一使用 `postValue()`

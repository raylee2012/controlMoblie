# Voice Control Mobile — 语音控制手机操作

## 概述

通过在 Android 手机上运行语音控制 App，用户使用语音指令操控系统操作和应用内操作。语音识别使用 Google SpeechRecognizer，指令解析由设备端本地 LLM 完成，操作执行通过 Accessibility Service 实现。

## 设备与约束

- **设备：** Redmi Note 13 Pro (Android)
- **RAM：** 6/8GB
- **ASR：** Google SpeechRecognizer（需网络）
- **LLM 推理：** 设备本地，使用 Qwen2.5-0.5B Q4 GGUF（~350MB）
- **操作执行：** Accessibility Service
- **覆盖范围：** 系统操作 + 应用内操作

## 整体架构

单一 Android App，包含以下核心组件：

```
┌─────────────────────────────────────────────────────────┐
│                    controlMoblie App                      │
│                                                          │
│   ┌──────────┐    ┌────────────┐    ┌────────────────┐   │
│   │  Voice   │    │    ASR     │    │   LLM Engine   │   │
│   │  Wake    │───▶│  (Google   │───▶│ (Qwen2.5-0.5B  │   │
│   │ (可选)   │    │  SpeechR)  │    │  llama.cpp)    │   │
│   └──────────┘    └────────────┘    └───────┬────────┘   │
│                                             │             │
│   ┌─────────────────────────────────────────▼──────────┐  │
│   │              Instruction Parser                     │  │
│   │   LLM output → JSON → {action, target, ...}        │  │
│   └──────────────────────┬─────────────────────────────┘  │
│                          │                                │
│   ┌──────────────────────▼─────────────────────────────┐  │
│   │              Execution Engine                       │  │
│   │   ┌─────────────────┐  ┌────────────────────────┐  │  │
│   │   │ Accessibiliity  │  │  系统 API 执行器       │  │  │
│   │   │ Service         │  │  (back/home/手势)       │  │  │
│   │   └─────────────────┘  └────────────────────────┘  │  │
│   └───────────────────┬─────────────────────────────────┘  │
│                       │                                    │
│   ┌───────────────────▼─────────────────────────────────┐  │
│   │  UI Overlay（透明浮窗）                              │  │
│   │  状态指示、手动开关、紧急停止                        │  │
│   └─────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

### 数据流

1. 用户语音 → Voice Wake（可选关键词唤醒）
2. → Google SpeechRecognizer → 识别文本
3. → LLM Engine → 结构化 JSON 指令
4. → Execution Engine → Accessibility Service / 系统 API
5. → 操作执行 → 反馈（Toast / 语音提示 / 浮窗状态）

## 组件设计

### 1. 语音唤醒与 ASR 模块

- **唤醒方式：** 可选（手动点击浮窗触发 / 配置唤醒词如"小助手"）
- **ASR 实现：** 封装 `android.speech.SpeechRecognizer`，实现 `RecognitionListener`
- **识别模式：** 持续识别模式，用户说话停顿 2 秒自动结束
- **状态反馈：** 浮窗实时显示状态（倾听中/识别中/思考中/执行中）和识别文本
- **错误处理：** 无网络时提示、超时重试、无识别结果时提示重新说话

### 2. LLM 指令解析引擎

- **模型：** Qwen2.5-0.5B Q4 GGUF（~350MB）
- **推理框架：** llama.cpp Android port 或 MNN
- **加载策略：** 首次启动下载模型，后续启动预加载到内存
- **输出格式：** 结构化 JSON

```json
{ "action": "click", "target": "微信" }
{ "action": "open_app", "package": "com.tencent.mm" }
{ "action": "navigate", "type": "back" }
{ "action": "scroll", "direction": "up", "distance": "half" }
{ "action": "type", "text": "你好" }
{ "action": "wait", "ms": 1000 }
{ "action": "sequence", "steps": [...] }
```

- **上下文注入：** LLM 推理前通过 Accessibility Service 获取当前屏幕可见文本，注入 prompt 帮助定位
- **容错：** 非 JSON 输出时提示用户重试；推理超时 5 秒中断

### 3. 执行引擎

- **核心实现：** 自定义 `AccessibilityService`，监听屏幕事件
- **元素定位：** `findAccessibilityNodeInfosByText()` / `findAccessibilityNodeInfosByViewId()`
- **指令映射：**

| 指令 | 实现 |
|------|------|
| click | `performAction(ACTION_CLICK)` |
| open_app | `startActivity(packageManager.getLaunchIntentForPackage())` |
| navigate:back | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| navigate:home | `performGlobalAction(GLOBAL_ACTION_HOME)` |
| scroll | `performGlobalAction()` + 手势 dispatch |
| type | 定位输入框 → dispatch 文字 |
| sequence | 顺序执行子指令，每条之间短暂延迟 |

- **安全措施：**
  - 仅在浮窗启用状态执行操作
  - 摇晃手机紧急停止
  - 敏感操作（如删除）用户二次确认

### 4. UI 浮窗（Overlay）

- **类型：** `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **内容：** 当前状态图标 + 识别文本预览 + 开关按钮
- **交互：** 点击浮窗可展开/收起详细面板
- **紧急停止：** 浮窗上提供急停按钮

## 不支持的范围

- 非 Google 语音服务设备（无 Google Play 服务）
- Android 版本低于 7.0（API 24）
- 无法获取 Accessibility Service 权限后的操控
- 不处理多手机/跨设备场景
- 不包含自定义语音唤醒词训练

## 后续规划

1. 创建 Android 工程（Kotlin + Jetpack Compose）
2. 实现 Accessibility Service 基础框架
3. 集成 Google SpeechRecognizer
4. 集成 llama.cpp Android，下载加载 Qwen2.5-0.5B 模型
5. 实现 LLM 指令解析与 JSON 输出
6. 实现执行引擎（指令映射 + 节点定位）
7. 实现 UI 浮窗
8. 端到端联调与测试

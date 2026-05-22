# 微信语音指令模板 — 设计方案

日期: 2026-05-23

## 概述

为语音控制 App 增加微信深度操作能力（聊天、朋友圈、小程序、公众号），通过预定义的指令模板实现。每个模板将一句语音指令映射为一组 UI 自动化步骤，通过无障碍服务按文字匹配执行。

## 动机

- 当前系统支持通用动作（点击、打开应用、滑动），但没有微信专有工作流
- 微信是核心使用场景
- 模板方案不需要大模型，简单可靠、易于维护
- 所有 UI 匹配基于 `findAccessibilityNodeInfosByText`，与微信版本解耦

## 架构

```
用户语音指令
        ↓
LlmEngine.simulateInference(userText)
        ↓
新增: matchWeChatTemplate(userText) — 在现有关键词匹配之前检查
        ↓ (匹配到)
提取参数 → 生成 Action.Sequence
        ↓ (未匹配到)
回退到现有 simulateInference 关键词匹配
        ↓
ExecutionEngine.execute(sequence) — 步骤间 Wait(300ms)
```

不改动现有的 `Action`、`ExecutionEngine`、`VoiceControlService`、`InstructionParser`。

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `llm/CommandTemplates.kt` | 新增 | 模板定义、关键词匹配、参数提取、Sequence 构建 |
| `llm/LlmEngine.kt` | 修改 | 在现有 `when` 块前增加模板匹配调用 |

## CommandTemplates 接口

```kotlin
object CommandTemplates {
    fun match(userText: String): List<Action>?  // 返回步骤列表，匹配不到返回 null
}
```

每条模板的结构：
```kotlin
data class Template(
    val keywords: List<String>,           // 关键词列表，如 ["发消息给", "给...发消息"]
    val extractParams: (String) -> Map<String, String>?,  // 返回 {target: "张三", text: "你好"}
    val buildSteps: (Map<String, String>) -> List<Action>
)
```

## 首批模板

### 1. 发消息

**说出:** "发消息给张三说你好" / "给张三发消息你好"

**步骤:**
```
Wait(500)                    ← 等微信界面就绪
Click("通讯录")              ← 进入通讯录
Wait(300)
Click(targetName)            ← 点击联系人
Wait(300)
Type(text)                   ← 输入消息
Wait(300)
Click("发送")                ← 点击发送
```

**兜底:** 如果找不到文字"发送"，尝试找 `isClickable=true` 且 `text` 或 `contentDescription` 含 "发送" 的节点，或在右下角找第一个可点击节点。

### 2. 看朋友圈

**说出:** "看朋友圈"

**步骤:**
```
Wait(500)
Click("发现")
Wait(300)
Click("朋友圈")
```

### 3. 发朋友圈

**说出:** "发朋友圈说XXX" / "发朋友圈XXX"

**步骤:**
```
Wait(500)
Click("发现")
Wait(300)
Click("朋友圈")
Wait(500)
Click("拍照分享") or 找相机图标节点  ← 可能需要按 resource-id 匹配
Wait(300)
Type(text)
Wait(300)
Click("发表")
```

### 4. 打开小程序

**说出:** "打开小程序XXX" / "打开XXX小程序"

**步骤（主页下拉方式）:**
```
Wait(500)
Scroll("down", "short")      ← 在主页下拉露出小程序列表
Wait(300)
Click(miniProgramName)       ← 按名点击
```

**备选（从发现页进）:**
```
Wait(500)
Click("发现")
Wait(300)
Click("小程序")
Wait(300)
Click(miniProgramName)       ← 如需滑动搜索则在列表里找
```

### 5. 搜索公众号

**说出:** "搜索公众号XXX"

**步骤:**
```
Wait(500)
Click("通讯录")
Wait(300)
Click("公众号")
Wait(300)
Click("搜索") or 找到搜索输入框  ← 可能需要 EditText focus
Wait(300)
Type(accountName)
Wait(500)
Click(accountName)           ← 点击搜索结果
```

### 6. 看某人的朋友圈

**说出:** "看张三的朋友圈"

**步骤:**
```
Wait(500)
Click("通讯录")
Wait(300)
Click("张三")
Wait(300)
Click("朋友圈") or 点击头像区域  ← 可能需要"相册"或滑动
Wait(300)
```

## 参数提取

基于简单关键词+正则的提取规则。示例：

| 输入 | 关键词 | 提取结果 |
|------|--------|---------|
| "发消息给张三说你好" | "发消息给" + "说" | target=张三, text=你好 |
| "给张三发消息你好啊" | "给" + "发消息" | target=张三, text=你好啊 |
| "发朋友圈今天天气不错" | "发朋友圈" | text=今天天气不错 |
| "打开小程序京东" | "打开小程序" | name=京东 |
| "搜索公众号人民日报" | "搜索公众号" | name=人民日报 |
| "看李四的朋友圈" | "看" + "的朋友圈" | target=李四 |

## 微信 UI 匹配策略

- 所有点击基于 `findAccessibilityNodeInfosByText` 文字匹配
- "发送" 按钮可能表现为不同形式，按优先级查找：
  1. `text="发送"`（最常见）
  2. `contentDescription="发送"`
  3. 无文字但有数据恢复 resource-id 的按钮
- "通讯录" 可能出现在底部导航栏或搜索栏旁边（两种都试）
- "发现" 是底部导航标签
- 小程序列表在微信主页聊天列表下方（下拉露出）
- 所有等待使用 `Action.Wait`，让界面动画和跳转完成
- 如果某步骤找不到目标文字，3 秒内报错 "未找到xxx"
- 所有节点使用后立即 recycle

## 错误处理

- 每步失败返回清晰中文："未找到通讯录" / "未找到张三" / "发送失败"
- Sequence 遇到第一个失败即停止（现有 `Sequence` 行为）
- 错误消息通过 TTS 播报（现有语音反馈流程）
- 修正微信状态后可重试同一指令

## 不在范围内

- 不做微信版本特定的 resource-id 或坐标匹配
- 不做单点点击和半屏滑动以外的复杂手势
- 不做多轮对话（每条指令独立执行）

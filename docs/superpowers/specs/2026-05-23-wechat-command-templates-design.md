# WeChat Voice Command Templates — Design Spec

Date: 2026-05-23

## Summary

Extend the voice control app to support WeChat operations (chat, moments, mini-programs, official accounts) using pre-defined command templates. Each template maps a voice command keyword to a sequence of UI actions executed via Accessibility Service with text-based node matching.

## Motivation

- Current system supports generic actions (click, open app, scroll) but has no WeChat-specific workflows
- WeChat is the primary use case
- Template-based approach keeps it simple, reliable, and maintainable without a large LLM
- All UI matching uses `findAccessibilityNodeInfosByText`, version-independent

## Architecture

```
User voice command
        ↓
LlmEngine.simulateInference(userText)
        ↓
New: matchWeChatTemplate(userText) — check BEFORE existing keyword matching
        ↓ (if matched)
Extract parameters from user text → build Action.Sequence
        ↓ (if not matched)
Fall through to existing simulateInference keyword matching
        ↓
ExecutionEngine.execute(sequence) — each step Wait(300) between actions
```

No changes to `Action`, `ExecutionEngine`, `VoiceControlService`, or `InstructionParser`.

## Files

| File | Action | Responsibility |
|------|--------|---------------|
| `llm/CommandTemplates.kt` | Create | Template definitions, keyword matching, parameter extraction, Action.Sequence builder |
| `llm/LlmEngine.kt` | Modify | Add `matchWeChatTemplate()` call before existing `when` block |

## CommandTemplates API

```kotlin
object CommandTemplates {
    fun match(userText: String): List<Action>?  // returns steps or null if no template matches
}
```

Each template entry:
```kotlin
data class Template(
    val keywords: List<String>,           // ["发消息给", "给...发消息"]
    val extractParams: (String) -> Map<String, String>?,  // → {target: "张三", text: "你好"}
    val buildSteps: (Map<String, String>) -> List<Action>
)
```

## Phase 1 Templates

### 1. Send Message — 发消息

**Voice:** "发消息给张三说你好" / "给张三发消息你好"

**Steps:**
```
Wait(500)                    ← wait for WeChat to show
Click("通讯录")              ← navigate to contacts
Wait(300)
Click(targetName)            ← click contact name
Wait(300)
Type(text)                   ← type message
Wait(300)
Click("发送")                ← press send (may need both "发送" and resource-id match)
```

**Retry:** If "发送" not found, try clicking a send button by searching for nodes with `isClickable=true` and `contentDescription` or `text` matching "发送" or a send icon description.

### 2. View Moments — 看朋友圈

**Voice:** "看朋友圈"

**Steps:**
```
Wait(500)
Click("发现")
Wait(300)
Click("朋友圈")
```

### 3. Post to Moments — 发朋友圈

**Voice:** "发朋友圈说XXX" / "发朋友圈XXX"

**Steps:**
```
Wait(500)
Click("发现")
Wait(300)
Click("朋友圈")
Wait(500)
Click("相机" or long-press the camera icon)  ← may need resource-id based match
Wait(300)
Type(text)
Wait(300)
Click("发表")
```

### 4. Open Mini Program — 打开小程序

**Voice:** "打开小程序XXX" / "打开XXX小程序"

**Steps:**
```
Wait(500)
Scroll("down", "short")      ← swipe down on main page to reveal mini-programs
Wait(300)
Click(miniProgramName)       ← click by name
```

**Alternative (if not recently used):**
```
Wait(500)
Click("发现")
Wait(300)
Click("小程序")
Wait(300)
Click(miniProgramName)       ← search/scroll if not visible
```

### 5. Search Official Account — 搜索公众号

**Voice:** "搜索公众号XXX"

**Steps:**
```
Wait(500)
Click("通讯录")
Wait(300)
Click("公众号")
Wait(300)
Click("搜索") or find search field  ← may use EditText focus
Wait(300)
Type(accountName)
Wait(500)
Click(accountName)           ← click search result
```

### 6. View Contact's Moments — 看XXX的朋友圈

**Voice:** "看张三的朋友圈"

**Steps:**
```
Wait(500)
Click("通讯录")
Wait(300)
Click("张三")
Wait(300)
Click("朋友圈") or the user's avatar/name area  ← may need "相册" or scroll
Wait(300)
```

## Parameter Extraction

Simple regex/keyword-based extraction. Examples:

| Input | Keywords | Extraction |
|-------|----------|------------|
| "发消息给张三说你好" | "发消息给" + "说" | target=张三, text=你好 |
| "给张三发消息你好啊" | "给" + "发消息" | target=张三, text=你好啊 |
| "发朋友圈今天天气不错" | "发朋友圈" | text=今天天气不错 |
| "打开小程序京东" | "打开小程序" | name=京东 |
| "搜索公众号人民日报" | "搜索公众号" | name=人民日报 |
| "看李四的朋友圈" | "看" + "的朋友圈" | target=李四 |

## WeChat UI Matching Strategy

- All clicks use `findAccessibilityNodeInfosByText` with text matching
- "发送" button may appear as:
  - `text="发送"` (most common)
  - `contentDescription="发送"` 
  - A button with no text but specific resource-id (fallback: first `isClickable` node in bottom-right area)
- "通讯录" may appear as bottom tab or in search bar (try both)
- "发现" is a bottom tab
- Mini-program list is below the chat list on the main WeChat page (swipe down reveals)
- All waits use `Action.Wait` to let UI transitions settle
- If a target text is not found within 3 seconds, the action fails with "未找到xxx"
- Nodes are always recycled after use

## Error Handling

- Each step that fails reports a clear Chinese message: "未找到通讯录" / "未找到张三" / "发送失败"
- Sequence stops on first failure (existing `Sequence` behavior)
- Failure message is spoken via TTS (existing voice feedback flow)
- User can retry the same command after fixing the state

## Non-Goals

- No WeChat version-specific resource IDs or coordinate-based matching
- No gesture simulation beyond single-point click and half-screen scroll
- No multi-turn dialogue (each command is independent)

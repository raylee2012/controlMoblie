# 微信语音指令模板 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 6 个微信深度操作指令模板（发消息、看朋友圈、发朋友圈、打开小程序、搜索公众号、看某人朋友圈），通过关键词匹配 + 参数提取生成 Action.Sequence。

**Architecture:** 新增 `CommandTemplates.kt` 管理所有模板定义、关键词匹配和 JSON 生成；修改 `LlmEngine.simulateInference` 在现有关键词匹配前先检查模板。

**Tech Stack:** Kotlin, existing Action sealed class, existing InstructionParser JSON parsing

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `llm/CommandTemplates.kt` | 创建 | 模板定义、匹配、参数提取、生成 Sequence JSON |
| `llm/LlmEngine.kt` | 修改 | `simulateInference` 开头调用 `CommandTemplates.match()` |

---

### Task 1: 创建 CommandTemplates

**Files:**
- Create: `app/src/main/java/com/controlmoblie/llm/CommandTemplates.kt`

- [ ] **Step 1: 创建文件**

Create `app/src/main/java/com/controlmoblie/llm/CommandTemplates.kt`:

```kotlin
package com.controlmoblie.llm

object CommandTemplates {

    data class Template(
        val keywords: List<String>,
        val extract: (String) -> Map<String, String>?,
        val buildJson: (Map<String, String>) -> String
    )

    private val templates: List<Template> = listOf(
        // 1. 发消息给XXX说YYY / 给XXX发消息YYY
        Template(
            keywords = listOf("发消息给", "发消息"),
            extract = { input ->
                val target: String
                val text: String
                if (input.contains("发消息给") && input.contains("说")) {
                    val afterKeyword = input.substringAfter("发消息给").trim()
                    val sayIdx = afterKeyword.indexOf("说")
                    if (sayIdx < 0) return@extract null
                    target = afterKeyword.substring(0, sayIdx).trim()
                    text = afterKeyword.substring(sayIdx + 1).trim()
                } else if (input.contains("给") && input.contains("发消息")) {
                    val afterGei = input.substringAfter("给").trim()
                    val msgIdx = afterGei.indexOf("发消息")
                    if (msgIdx < 0) return@extract null
                    target = afterGei.substring(0, msgIdx).trim()
                    text = afterGei.substring(msgIdx + 3).trim()
                } else {
                    return@extract null
                }
                if (target.isBlank() || text.isBlank()) null
                else mapOf("target" to target, "text" to text)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500),
                    step("click", "通讯录"),
                    step("wait", 300),
                    step("click", p["target"] ?: ""),
                    step("wait", 300),
                    step("type", p["text"] ?: ""),
                    step("wait", 300),
                    step("click", "发送"),
                )
            }
        ),
        // 2. 看朋友圈
        Template(
            keywords = listOf("看朋友圈"),
            extract = { mapOf() },
            buildJson = {
                buildSequenceJson(
                    step("wait", 500),
                    step("click", "发现"),
                    step("wait", 300),
                    step("click", "朋友圈"),
                )
            }
        ),
        // 3. 发朋友圈说XXX / 发朋友圈XXX
        Template(
            keywords = listOf("发朋友圈"),
            extract = { input ->
                val text = input.substringAfter("发朋友圈").trim().removePrefix("说").trim()
                if (text.isBlank()) null else mapOf("text" to text)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500),
                    step("click", "发现"),
                    step("wait", 300),
                    step("click", "朋友圈"),
                    step("wait", 500),
                    step("click", "拍照分享"),
                    step("wait", 300),
                    step("type", p["text"] ?: ""),
                    step("wait", 300),
                    step("click", "发表"),
                )
            }
        ),
        // 4. 打开小程序XXX / 打开XXX小程序
        Template(
            keywords = listOf("打开小程序", "小程序"),
            extract = { input ->
                val name = if (input.contains("打开小程序")) {
                    input.substringAfter("打开小程序").trim()
                } else {
                    input.substringBefore("小程序").trim().replace("打开", "").trim()
                }
                if (name.isBlank()) null else mapOf("name" to name)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500),
                    step("scroll", mapOf("direction" to "down", "distance" to "short")),
                    step("wait", 300),
                    step("click", p["name"] ?: ""),
                )
            }
        ),
        // 5. 搜索公众号XXX
        Template(
            keywords = listOf("搜索公众号"),
            extract = { input ->
                val name = input.substringAfter("搜索公众号").trim()
                if (name.isBlank()) null else mapOf("name" to name)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500),
                    step("click", "通讯录"),
                    step("wait", 300),
                    step("click", "公众号"),
                    step("wait", 300),
                    step("type", p["name"] ?: ""),
                    step("wait", 500),
                    step("click", p["name"] ?: ""),
                )
            }
        ),
        // 6. 看XXX的朋友圈
        Template(
            keywords = listOf("的朋友圈"),
            extract = { input ->
                val idx = input.indexOf("的朋友圈")
                if (idx < 0) return@extract null
                val target = input.substring(if (input.startsWith("看")) 1 else 0, idx).trim()
                if (target.isBlank()) null else mapOf("target" to target)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500),
                    step("click", "通讯录"),
                    step("wait", 300),
                    step("click", p["target"] ?: ""),
                    step("wait", 300),
                    step("click", "朋友圈"),
                )
            }
        ),
    )

    fun match(userText: String): String? {
        for (tpl in templates) {
            val matched = tpl.keywords.any { userText.contains(it) }
            if (matched) {
                val params = tpl.extract(userText) ?: continue
                return tpl.buildJson(params)
            }
        }
        return null
    }

    private fun step(type: String, target: String): String {
        return """{"action":"$type","target":"$target"}"""
    }

    private fun step(type: String, ms: Long): String {
        return """{"action":"$type","ms":$ms}"""
    }

    private fun step(type: String, props: Map<String, String>): String {
        val inner = props.entries.joinToString(",") { """"${it.key}":"${it.value}"""" }
        return """{"action":"$type",$inner}"""
    }

    private fun buildSequenceJson(vararg steps: String): String {
        val stepsJson = steps.joinToString(",")
        return """{"action":"sequence","steps":[$stepsJson]}"""
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```
git add app/src/main/java/com/controlmoblie/llm/CommandTemplates.kt
git commit -m "feat: add WeChat command templates"
```

---

### Task 2: 集成到 LlmEngine

**Files:**
- Modify: `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`

- [ ] **Step 1: 在 simulateInference 最前面加模板匹配**

Read `app/src/main/java/com/controlmoblie/llm/LlmEngine.kt`, in `simulateInference` method, insert before the existing `when` block (line 94):

Change from:
```kotlin
    private fun simulateInference(userText: String): String {
        return when {
```

To:
```kotlin
    private fun simulateInference(userText: String): String {
        val templateJson = CommandTemplates.match(userText)
        if (templateJson != null) return templateJson

        return when {
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 构建 APK**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```
git add app/src/main/java/com/controlmoblie/llm/LlmEngine.kt
git commit -m "feat: integrate WeChat command templates into LlmEngine"
```

---

## 自审

1. **Spec 覆盖:** 6 个模板全部实现在 Task 1，集成在 Task 2。参数提取、JSON 生成、兜底策略覆盖。
2. **无占位符:** 所有代码完整给出，无 TBD/TODO。
3. **类型一致:** `CommandTemplates.match()` 返回 `String?`（JSON），`LlmEngine.simulateInference` 也是返回 `String`，完全兼容。Sequence JSON 结构与 `InstructionParser.parseAction` 的 `"sequence"` 分支一致。

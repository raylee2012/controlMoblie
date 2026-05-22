package com.controlmoblie.llm

object CommandTemplates {

    private data class Template(
        val keywords: List<String>,
        val extract: (String) -> Map<String, String>?,
        val buildJson: (Map<String, String>) -> String
    )

    private val templates: List<Template> = listOf(
        Template(
            keywords = listOf("发消息给", "发消息"),
            extract = { input -> extractSendMessage(input) },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500), step("click", "通讯录"), step("wait", 300),
                    step("click", p["target"] ?: ""), step("wait", 300),
                    step("type", p["text"] ?: ""), step("wait", 300),
                    step("click", "发送"),
                )
            }
        ),
        Template(
            keywords = listOf("看朋友圈"),
            extract = { emptyMap() },
            buildJson = {
                buildSequenceJson(
                    step("wait", 500), step("click", "发现"), step("wait", 300),
                    step("click", "朋友圈"),
                )
            }
        ),
        Template(
            keywords = listOf("发朋友圈"),
            extract = { input ->
                val text = input.substringAfter("发朋友圈").trim().removePrefix("说").trim()
                if (text.isBlank()) null else mapOf("text" to text)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500), step("click", "发现"), step("wait", 300),
                    step("click", "朋友圈"), step("wait", 500),
                    step("click", "拍照分享"), step("wait", 300),
                    step("type", p["text"] ?: ""), step("wait", 300),
                    step("click", "发表"),
                )
            }
        ),
        Template(
            keywords = listOf("打开小程序"),
            extract = { input ->
                val name = input.substringAfter("打开小程序").trim()
                if (name.isBlank()) null else mapOf("name" to name)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500),
                    step("scroll", mapOf("direction" to "down", "distance" to "short")),
                    step("wait", 300), step("click", p["name"] ?: ""),
                )
            }
        ),
        Template(
            keywords = listOf("搜索公众号"),
            extract = { input ->
                val name = input.substringAfter("搜索公众号").trim()
                if (name.isBlank()) null else mapOf("name" to name)
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500), step("click", "通讯录"), step("wait", 300),
                    step("click", "公众号"), step("wait", 300),
                    step("type", p["name"] ?: ""), step("wait", 500),
                    step("click", p["name"] ?: ""),
                )
            }
        ),
        Template(
            keywords = listOf("的朋友圈"),
            extract = { input ->
                val idx = input.indexOf("的朋友圈")
                if (idx < 0) null
                else {
                    val target = input.substring(0, idx).trim().removePrefix("看").trim()
                    if (target.isBlank()) null else mapOf("target" to target)
                }
            },
            buildJson = { p ->
                buildSequenceJson(
                    step("wait", 500), step("click", "通讯录"), step("wait", 300),
                    step("click", p["target"] ?: ""), step("wait", 300),
                    step("click", "朋友圈"),
                )
            }
        ),
    )

    private fun extractSendMessage(input: String): Map<String, String>? {
        if (input.contains("发消息给") && input.contains("说")) {
            val after = input.substringAfter("发消息给").trim()
            val sayIdx = after.indexOf("说")
            if (sayIdx < 0) return null
            val target = after.substring(0, sayIdx).trim()
            val text = after.substring(sayIdx + 1).trim()
            if (target.isBlank() || text.isBlank()) return null
            return mapOf("target" to target, "text" to text)
        }
        if (input.contains("给") && input.contains("发消息")) {
            val afterGei = input.substringAfter("给").trim()
            val msgIdx = afterGei.indexOf("发消息")
            if (msgIdx < 0) return null
            val target = afterGei.substring(0, msgIdx).trim()
            val text = afterGei.substring(msgIdx + 3).trim()
            if (target.isBlank() || text.isBlank()) return null
            return mapOf("target" to target, "text" to text)
        }
        return null
    }

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

    private fun step(type: String, target: String): String =
        """{"action":"$type","target":"$target"}"""

    private fun step(type: String, ms: Long): String =
        """{"action":"$type","ms":$ms}"""

    private fun step(type: String, props: Map<String, String>): String {
        val inner = props.entries.joinToString(",") { """"${it.key}":"${it.value}"""" }
        return """{"action":"$type",$inner}"""
    }

    private fun buildSequenceJson(vararg steps: String): String {
        val stepsJson = steps.joinToString(",")
        return """{"action":"sequence","steps":[$stepsJson]}"""
    }
}

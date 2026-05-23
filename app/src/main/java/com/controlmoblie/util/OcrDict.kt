package com.controlmoblie.util

import java.io.File

object OcrDict {
    private var chars: List<String> = emptyList()

    fun load(path: String) {
        val lines = File(path).readLines()
        chars = lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) trimmed else null
        }
    }

    fun decode(indices: IntArray): String {
        if (chars.isEmpty()) return ""
        val sb = StringBuilder()
        var lastIdx = -1
        for (idx in indices) {
            if (idx in chars.indices && idx != lastIdx) {
                sb.append(chars[idx])
            }
            lastIdx = idx
        }
        return sb.toString()
    }

    val size: Int get() = chars.size
    val isLoaded: Boolean get() = chars.isNotEmpty()
}

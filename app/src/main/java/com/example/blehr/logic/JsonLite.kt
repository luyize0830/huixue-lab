package com.example.blehr.logic

/**
 * 一个只够用的 JSON 取值器。
 *
 * 为什么不用 org.json：它属于 Android 框架，在 JVM 单测里会抛
 * "not mocked"，而我们最想测的恰恰是"模型返回了脏东西时会不会崩"。
 * 所以这里手写一层：只解析我们约定的扁平结构（reply / actionIds / minutes），
 * 遇到任何不认识的东西一律返回 null，绝不猜。
 */
object JsonLite {

    /** 去掉 ```json ... ``` 围栏，以及模型爱加的前后客套话 */
    fun stripFences(raw: String): String {
        var s = raw.trim()
        s = Regex("```(?:json|JSON)?\\s*").replace(s, "")
        s = s.replace("```", "")
        return s.trim()
    }

    /** 从文本里截出第一个完整的 {...}，字符串里的花括号不算 */
    fun extractObject(raw: String): String? {
        val s = stripFences(raw)
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** 找到 "key": 之后第一个非空白字符的位置 */
    private fun valueStart(obj: String, key: String): Int? {
        val needle = "\"$key\""
        var from = 0
        while (true) {
            val k = obj.indexOf(needle, from)
            if (k < 0) return null
            var i = k + needle.length
            while (i < obj.length && obj[i].isWhitespace()) i++
            if (i < obj.length && obj[i] == ':') {
                i++
                while (i < obj.length && obj[i].isWhitespace()) i++
                return i
            }
            from = k + needle.length
        }
    }

    /** 从 start（必须指向引号）读一个 JSON 字符串，返回内容和下一个位置 */
    private fun readQuoted(s: String, start: Int): Pair<String, Int>? {
        if (start >= s.length || s[start] != '"') return null
        var i = start + 1
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val e = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < s.length) {
                            val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) sb.append(code.toChar())
                            i += 4
                        }
                    }
                    else -> sb.append(e)
                }
                i += 2
            } else if (c == '"') {
                return sb.toString() to (i + 1)
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }

    fun readString(obj: String, key: String): String? {
        val at = valueStart(obj, key) ?: return null
        return readQuoted(obj, at)?.first
    }

    fun readStringArray(obj: String, key: String): List<String> {
        var i = valueStart(obj, key) ?: return emptyList()
        if (i >= obj.length || obj[i] != '[') return emptyList()
        i++
        val out = ArrayList<String>()
        while (i < obj.length) {
            while (i < obj.length && (obj[i].isWhitespace() || obj[i] == ',')) i++
            if (i >= obj.length || obj[i] == ']') break
            val parsed = readQuoted(obj, i) ?: break
            out.add(parsed.first)
            i = parsed.second
        }
        return out
    }

    fun readInt(obj: String, key: String): Int? {
        val i = valueStart(obj, key) ?: return null
        var j = i
        while (j < obj.length && (obj[j] == '-' || obj[j].isDigit())) j++
        if (j == i) return null
        return obj.substring(i, j).toIntOrNull()
    }
}
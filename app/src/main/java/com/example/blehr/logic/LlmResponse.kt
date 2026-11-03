package com.example.blehr.logic

import org.json.JSONException
import org.json.JSONObject

/** 连接测试和正式聊天共用同一条成功判据，不把网页或空响应当作模型已接入。 */
object LlmResponse {
    sealed class Result {
        data class Ok(val suggestion: LlmSuggestion) : Result()
        data class Fail(val reason: String) : Result()
    }

    fun decode(code: Int, raw: String): Result {
        if (code !in 200..299) return Result.Fail(LlmErrors.hint(code))
        val content = try {
            val root = JSONObject(raw)
            if (root.has("error") && !root.isNull("error")) return Result.Fail(LlmErrors.BAD_REPLY)
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            val role = message?.opt("role")
            if (role != null && role != JSONObject.NULL && role != "assistant") {
                return Result.Fail(LlmErrors.BAD_REPLY)
            }
            message?.opt("content") as? String
        } catch (_: JSONException) {
            null
        }
        val suggestion = LlmReplyParser.parse(content) ?: return Result.Fail(LlmErrors.BAD_REPLY)
        return Result.Ok(suggestion)
    }
}

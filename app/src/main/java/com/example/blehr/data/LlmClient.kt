package com.example.blehr.data

import android.content.Context
import com.example.blehr.logic.ChatContext
import com.example.blehr.logic.ChatTurn
import com.example.blehr.logic.JsonLite
import com.example.blehr.logic.LlmReplyParser
import com.example.blehr.logic.LlmSuggestion
import com.example.blehr.logic.Persona
import com.example.blehr.model.ActionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 大模型开关与密钥。
 *
 * 为什么密钥放本机 SharedPreferences 而不是"我们服务器"：
 * 我们没有服务器，也不想替用户托管密钥。代价是它明文躺在应用私有目录里，
 * 所以界面上必须说清楚，并且提供一键清除。
 */
object LlmConfig {
    private const val PREFS = "huixue_llm"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_API = "api_key"
    private const val KEY_MODEL = "model"

    const val DEFAULT_MODEL = "deepseek-chat"
    const val BASE_URL = "https://api.deepseek.com/chat/completions"

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, v).apply()
    }

    fun apiKey(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, "").orEmpty()

    fun setApiKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API, key.trim()).apply()
    }

    fun model(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODEL, DEFAULT_MODEL)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

    fun isReady(ctx: Context): Boolean = enabled(ctx) && apiKey(ctx).isNotBlank()

    /** 明文存放的代价，界面上要能一眼看到"删掉它" */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * 一次对话请求。任何失败（没网、超时、401、返回不是 JSON）都返回 null，
 * 由界面退回本地规则 —— 用户不该因为模型挂了就用不了。
 */
object LlmClient {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun ask(
        ctx: Context,
        text: String,
        pool: List<ActionItem>,
        chat: ChatContext
    ): LlmSuggestion? = withContext(Dispatchers.IO) {
        val key = LlmConfig.apiKey(ctx)
        if (key.isBlank()) return@withContext null

        val body = buildBody(
            model = LlmConfig.model(ctx),
            system = Persona.system(
                hour = chat.hour,
                mood = chat.mood,
                memory = chat.memory,
                experimentDay = chat.experimentDay,
                totalRecords = chat.totalRecords
            ),
            user = Persona.user(text, pool, chat.hour, chat.band),
            history = chat.history
        )

        runCatching {
            val conn = (URL(LlmConfig.BASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $key")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) return@runCatching null

            // 先从响应里取 message.content，再解析里面那段 JSON
            val content = JsonLite.readString(raw, "content")
            LlmReplyParser.parse(content)
        }.getOrNull()
    }

    private fun buildBody(
        model: String,
        system: String,
        user: String,
        history: List<ChatTurn>
    ): String {
        val sb = StringBuilder()
        sb.append("{\"model\":").append(quote(model))
        sb.append(",\"messages\":[")
        sb.append("{\"role\":\"system\",\"content\":").append(quote(system)).append("}")
        history.takeLast(6).forEach { t ->
            sb.append(",{\"role\":").append(quote(if (t.fromUser) "user" else "assistant"))
                .append(",\"content\":").append(quote(t.text)).append("}")
        }
        sb.append(",{\"role\":\"user\",\"content\":").append(quote(user)).append("}")
        sb.append("],\"temperature\":0.8,\"max_tokens\":400,\"stream\":false}")
        return sb.toString()
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append("\"").toString()
    }
}
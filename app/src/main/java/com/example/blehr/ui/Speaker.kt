package com.example.blehr.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 让小夜"念出来"。
 *
 * 用的是手机自带的 TTS 引擎（很多机器上是讯飞/小爱/Google 的那个），所以：
 * - 不要录音权限、不上传任何音频；
 * - 也不打包音色，包体不涨；
 * - 代价是音色取决于用户手机，换台机器声音就变了——这一点在"我的"里写明了。
 *
 * 一句太长会念很久，所以超过 120 字就截断，末尾留个省略号：真人说话也会喘气。
 */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var langReady = false

    var ready by mutableStateOf(false)
        private set
    var reason: String? by mutableStateOf(null)
        private set

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ready = false
                reason = "这台手机没有可用的语音引擎"
                return@TextToSpeech
            }
            configure()
        }
    }

    private fun configure() {
        val set = runCatching { tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) }.getOrNull()
        langReady = set != null && set != TextToSpeech.LANG_MISSING_DATA && set != TextToSpeech.LANG_NOT_SUPPORTED
        ready = langReady
        if (!langReady) reason = "系统里没装中文语音包，去设置里下载一个就能用"
        runCatching {
            tts?.setSpeechRate(1.02f)
            tts?.setPitch(1.06f)
        }
    }

    /** flush = true 会打断正在念的那句（用户手动点"再听一遍"时就该这样）。 */
    fun say(text: String, flush: Boolean = false) {
        if (tts == null) {
            init()
            return
        }
        if (!langReady) configure()
        if (!ready) return
        val clean = text.trim().replace("\n", "，").replace("||", "，")
        if (clean.isBlank()) return
        val cut = if (clean.length > 120) clean.take(120) + "…" else clean
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { tts?.speak(cut, mode, null, "xiaoye-${System.nanoTime()}") }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context.applicationContext) }
    DisposableEffect(Unit) {
        speaker.init()
        onDispose {
            // 离开页面就闭嘴：App 在后台还念稿子，是最容易让人尴尬的一种"陪伴"。
            speaker.shutdown()
        }
    }
    return speaker
}
package com.example.blehr.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blehr.logic.AmbientTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 三段音景的合成公式。
 *
 * 刻意写成"时间 → 采样值"的纯函数：不持有状态、不碰 Android API，
 * 所以可以在 JVM 单测里直接验证（幅度不越界、没有 NaN、不同音景确实不一样）。
 * 真正放音的 [AmbientPlayer] 只负责按块调用它。
 */
object Synth {

    /** 采样率：22050 对这三段"低频 + 噪声"足够，还省一半 CPU */
    const val SAMPLE_RATE = 22050

    /** 相位偏移表：固定值而不是随机数，保证同一时刻的采样永远是同一个值（可测） */
    private val PHASES = doubleArrayOf(0.0, 0.7, 1.9, 2.6, 3.3, 4.1, 4.8, 5.5, 0.35, 1.2, 2.1, 5.9)

    /** 噪声分量用的频率：彼此不成整数比，叠起来像"沙沙"而不是"嗡嗡" */
    private val HISS_FREQS = doubleArrayOf(
        731.0, 947.0, 1213.0, 1489.0, 1777.0, 2081.0,
        2357.0, 2671.0, 3011.0, 3373.0, 3709.0, 4093.0
    )

    /** 弦乐组：F3 / A3 / C4 */
    private val NIGHT_NOTES = doubleArrayOf(174.61, 220.0, 261.63)

    /** 呼吸段的两个音：C3 / G3 */
    private val BREATH_NOTES = doubleArrayOf(130.81, 196.0)

    /** 白噪声（用不会成整数比的正弦堆出来，避免"嗡"的金属感） */
    private fun hiss(t: Double): Double {
        var sum = 0.0
        for (i in HISS_FREQS.indices) {
            sum += sin(2.0 * PI * HISS_FREQS[i] * t + PHASES[i])
        }
        return sum / HISS_FREQS.size
    }

    /**
     * 某一段音景在 t 秒处的采样值，范围固定在 [-1, 1]。
     * 调用方负责再乘主音量。
     */
    fun sample(track: AmbientTrack, t: Double): Float {
        val v = when (track) {
            // 雨：以噪声为主，用一个很慢的 LFO 让"雨势"有起伏，再叠一点点低频雨幕感
            AmbientTrack.RAIN -> {
                val lfo = 0.55 + 0.25 * sin(2.0 * PI * 0.07 * t)
                val base = hiss(t) * 0.42 * lfo
                val muffled = sin(2.0 * PI * 96.0 * t + 0.4) * 0.05
                base + muffled + droplet(t)
            }
            // 夜的 Pad：三个音缓慢拍频 + 极轻的底噪，听起来像远处有风
            AmbientTrack.NIGHT -> {
                var sum = 0.0
                for (i in NIGHT_NOTES.indices) {
                    val detune = 1.0 + 0.0015 * sin(2.0 * PI * (0.05 + i * 0.013) * t + PHASES[i])
                    sum += sin(2.0 * PI * NIGHT_NOTES[i] * detune * t) / NIGHT_NOTES.size
                }
                val breathe = 0.6 + 0.4 * sin(2.0 * PI * 0.045 * t - PI / 2)
                sum * 0.5 * breathe + hiss(t) * 0.05
            }
            // 呼吸：10 秒一个来回（吸 4 秒、呼 6 秒），音量跟着涨落，像有人在你旁边慢慢呼吸
            AmbientTrack.BREATH -> {
                val cycle = 0.5 - 0.5 * kotlin.math.cos(2.0 * PI * 0.1 * t)
                var sum = 0.0
                for (i in BREATH_NOTES.indices) {
                    sum += sin(2.0 * PI * BREATH_NOTES[i] * t + PHASES[i]) / BREATH_NOTES.size
                }
                sum * 0.45 * cycle + hiss(t) * 0.035 * cycle
            }
        }
        return v.coerceIn(-1.0, 1.0).toFloat()
    }

    /** 雨滴：每 1.7 秒一次，指数衰减的短促咔哒，音量很小，只为"有颗粒" */
    private fun droplet(t: Double): Double {
        val period = 1.7
        val phase = (t % period) / period
        if (phase > 0.02) return 0.0
        val decay = exp(-phase * 120.0)
        return sin(2.0 * PI * 2400.0 * t) * decay * 0.06
    }
}

/**
 * 内置播放器：用 AudioTrack 边算边放，不读文件、不联网、不要权限。
 *
 * 生命周期就按"用户手上这个页面"来：离开页面会停（在 Compose 里由
 * `rememberAmbientPlayer` 的 DisposableEffect 负责），没有被系统杀后台的包袱，
 * 也不申请前台服务权限 —— 这是刻意的取舍，写进 README 的已知限制。
 */
class AmbientPlayer {

    var playing by mutableStateOf(false)
        private set

    var current by mutableStateOf<AmbientTrack?>(null)
        private set

    @Volatile private var vol = 0.6f
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    var volume: Float
        get() = vol
        set(value) {
            vol = value.coerceIn(0f, 1f)
            runCatching { track?.setVolume(vol) }
        }

    fun start(kind: AmbientTrack) {
        if (playing && current == kind) return
        stop()
        current = kind

        val minBuf = AudioTrack.getMinBufferSize(
            Synth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = if (minBuf > 0) minBuf * 2 else 8192

        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Synth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = at
        at.setVolume(vol)
        runCatching { at.play() }

        running = true
        playing = true

        val maxMs = kind.minutes * 60_000L * 3
        worker = Thread {
            val chunk = ShortArray(1024)
            var index = 0L
            var playedMs = 0L
            val fadeSamples = 1.2 * Synth.SAMPLE_RATE
            while (running) {
                for (i in chunk.indices) {
                    val t = (index + i).toDouble() / Synth.SAMPLE_RATE
                    var v = Synth.sample(kind, t)
                    if (index + i < fadeSamples) v *= ((index + i) / fadeSamples).toFloat()
                    chunk[i] = (v * 32000).toInt().coerceIn(-32768, 32767).toShort()
                }
                val written = runCatching { at.write(chunk, 0, chunk.size) }.getOrDefault(0)
                if (written <= 0) break
                index += chunk.size
                playedMs += chunk.size * 1000L / Synth.SAMPLE_RATE
                // 到建议时长的 3 倍就自己停，防止有人忘了关
                if (playedMs > maxMs) break
                Thread.sleep(4)
            }
            if (playing) {
                runCatching { at.stop() }
                runCatching { at.release() }
                playing = false
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        val w = worker
        worker = null
        runCatching { w?.join(600) }
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
        playing = false
    }

    fun toggle(kind: AmbientTrack) {
        if (playing && current == kind) stop() else start(kind)
    }

    fun release() {
        stop()
        current = null
    }
}
package com.example.blehr

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Health Connect 要求的"为什么需要这些数据"说明页。
 *
 * Android 14 起，Health Connect 的授权弹窗会先找这个 Activity；
 * 没有声明时，弹窗在某些机型和 ROM 上会一闪而过（用户看到的就是"点了没反应"）。
 * 所以这一页不是摆设，是授权链路的一部分。
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = """
                回血实验室 需要读取两类健康数据：

                · 步数：判断你有多久没起身走动
                · 睡眠时长：只用于展示，暂时不进状态评分

                这些数据只读、只在本机使用，不会上传，也不会写入你的健康档案。
                你随时可以在系统的「健康数据」设置里撤回授权。
            """.trimIndent()
            textSize = 16f
            setPadding(64, 120, 64, 64)
            setLineSpacing(12f, 1.2f)
        }
        setContentView(tv)
    }
}
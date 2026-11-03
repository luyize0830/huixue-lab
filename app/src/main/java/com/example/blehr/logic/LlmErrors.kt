package com.example.blehr.logic

/**
 * 连不上模型的那些原因，和它们对应的"人话"。
 *
 * 为什么要单独抽出来：以前 `LlmClient.ask` 失败就返回 null，界面只能写一句
 * "刚才没连上模型"——用户看到这句话完全不知道该改哪里。401（Key 失效）和
 * 404（地址填错）的修法完全相反，必须分开说。
 *
 * 抽成纯函数是为了能单测：这些文案以后要改，改动不能把"哪种错配哪句话"改坏。
 */
object LlmErrors {

    /** 还没接模型（没填 Key 或开关没开）。这不是"连不上"，是"没开始"。 */
    const val NOT_CONFIGURED = "还没接入你自己的模型，现在用的是本地规则"

    /** HTTP 状态码 → 一句话怎么修。 */
    fun hint(code: Int): String = when (code) {
        400 -> "请求被拒了（400）：多半是模型名不对，检查一下是不是 deepseek-chat"
        401 -> "Key 不对或已经失效：去控制台新建一个 Key，重新填进来"
        402 -> "账户余额不够：这个 Key 能用，但这个服务要充值"
        403 -> "这个 Key 没有调用权限（403）"
        404 -> "地址不对（404）：一般填到 /v1 就行，不要带多余的路径"
        429 -> "请求太频繁了，等一分钟再试"
        in 500..599 -> "服务端出错了（$code），过一会儿再试"
        else -> "服务端返回 $code"
    }

    /** 异常 → 一句话怎么修。 */
    fun fromException(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "域名解析不了：检查网络，或者地址拼错了"
        is java.net.SocketTimeoutException -> "连上了但一直没回应：网络不稳，或者这个地址不通"
        is javax.net.ssl.SSLException -> "SSL 握手失败：地址应该是 https://"
        is java.net.ConnectException -> "连不上这个地址：检查网络或地址"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    /** 请求通了、但回来的东西不是我们要的格式。 */
    const val BAD_REPLY = "模型回的话我没看懂：这个地址可能不是 OpenAI 兼容接口"
}
package app.sift.domain.util

/** 可注入的时钟，便于单测固定时间。实现放外层（System.currentTimeMillis）。 */
fun interface Clock {
    fun nowMillis(): Long
}

/** 可注入的 ID 生成器，便于单测固定 ID。实现放外层（UUID）。 */
fun interface IdProvider {
    fun newId(): String
}

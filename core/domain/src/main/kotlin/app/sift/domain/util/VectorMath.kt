package app.sift.domain.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 向量检索的纯数学（RAG 语义检索的核心）。个人规模数据用"暴力"逐个算余弦即可，毫秒级，
 * 无需引入重型向量库。全是纯函数，便于 JVM 单测。
 */

/** 余弦相似度：看两个向量的夹角。1=同向(最像)，0=无关，-1=相反。 */
fun cosine(a: FloatArray, b: FloatArray): Float {
    if (a.isEmpty() || a.size != b.size) return 0f
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom == 0f) 0f else dot / denom
}

/** 暴力 Top-K：把 query 和每个候选逐个算余弦，排序取前 k。返回 (候选, 相似度)。 */
fun <T> topKByCosine(
    query: FloatArray,
    items: List<T>,
    k: Int,
    vectorOf: (T) -> FloatArray,
): List<Pair<T, Float>> =
    items.map { it to cosine(query, vectorOf(it)) }
        .sortedByDescending { it.second }
        .take(k)

/** FloatArray ↔ ByteArray，便于把向量以 BLOB 存进 Room（小端序）。 */
fun FloatArray.toBytes(): ByteArray {
    val buf = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buf.putFloat(it) }
    return buf.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buf = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buf.float }
}

package com.bookkeeping.app.parser

/**
 * 较长窗口的键值去重（可持久化）。
 * 无障碍服务停留在结果页时会持续收到内容变化事件，服务重启后内存状态也会丢失，
 * 因此用「金额 + 对象 + 结果页文案」作为键，在 [windowMs] 内只放行一次，并通过 [persist] 落盘。
 */
class DedupWindow(
    private val windowMs: Long,
    initial: Map<String, Long> = emptyMap(),
    private val persist: (Map<String, Long>) -> Unit = {}
) {
    private val hits = HashMap<String, Long>(initial)

    /** 窗口内首次出现返回 true（并记录），否则返回 false */
    @Synchronized
    fun tryAcquire(key: String, now: Long = System.currentTimeMillis()): Boolean {
        hits.entries.removeIf { now - it.value > windowMs || it.value > now }
        if (hits.containsKey(key)) return false
        hits[key] = now
        persist(HashMap(hits))
        return true
    }

    @Synchronized
    fun snapshot(): Map<String, Long> = HashMap(hits)
}

package com.example.fishingmapkmp

data class NavSnapshot(
    val timestamp: Long,
    val speedKnots: Double,
    val courseDegrees: Double,
    val latitude: Double,
    val longitude: Double
)

class SlidingWindowBuffer(private val windowSize: Int = 15) { // 每 2 秒更新一次，15 筆約為 30 秒的時序視窗
    private val buffer = mutableListOf<NavSnapshot>()

    /**
     * 新增定位快照，當視窗填滿（達到 windowSize）時，會回傳完整的時序列表供 AI 預測
     */
    fun addSample(snapshot: NavSnapshot): List<NavSnapshot>? {
        buffer.add(snapshot)
        if (buffer.size > windowSize) {
            buffer.removeAt(0) // 移除最舊的一筆，保持滑動視窗大小
        }
        return if (buffer.size == windowSize) buffer.toList() else null
    }
}
package com.omerceren.barkodu

/** Presentation identity is independent of throttle, gyro values and warning preferences. */
class ScanGate(private val absenceMs: Long = 1_200, private val minimumEmptyFrames: Int = 6) {
    enum class Decision {ADD, IGNORE}
    private var lockedCode = ""
    private var missingSince = -1L
    private var emptyFrames = 0
    fun present() {missingSince = -1; emptyFrames = 0}
    @Suppress("UNUSED_PARAMETER")
    fun observe(code: String?, now: Long, warningEnabled: Boolean = true): Decision {
        if(code == null) {
            if(missingSince < 0) missingSince = now
            emptyFrames++
            if(emptyFrames >= minimumEmptyFrames && now - missingSince >= absenceMs) lockedCode = ""
            return Decision.IGNORE
        }
        present()
        if(code == lockedCode) return Decision.IGNORE
        lockedCode = code
        return Decision.ADD
    }
    fun pause() {present()}
    fun isHeld(code: String): Boolean = lockedCode == code
}

/** Reject isolated and alternating decodes; never truncate or pad the decoder's string. */
class DecodeConsensus(private val requiredReads: Int = 3, private val stableMs: Long = 120, private val maximumGapMs: Long = 1_000) {
    private var candidate: String? = null
    private var firstSeen = 0L
    private var lastSeen = 0L
    private var reads = 0
    fun observe(code: String?, now: Long): String? {
        // Camera decodes are intermittent. A brief missed frame is not a conflicting reading.
        if(code == null) {if(now-lastSeen>maximumGapMs||now<lastSeen)reset(); return null}
        // A slow decoder with matching consecutive results is not evidence of absence.
        // Only analyzed misses expire evidence; camera stop explicitly resets this object.
        if(code != candidate || now < lastSeen) {candidate = code; firstSeen = now; reads = 0}
        lastSeen = now; reads = minOf(requiredReads,reads+1)
        return code.takeIf {reads >= requiredReads && now - firstSeen >= stableMs}
    }
    fun reset() {candidate = null; reads = 0}
}

/** Independent clocks for frame workload and confirmed-event admission. */
class ScanThrottle {
    private var last = Long.MIN_VALUE
    fun shouldProcess(now: Long, delayMs: Int): Boolean {
        if(last != Long.MIN_VALUE && now >= last && now - last < delayMs) return false
        last = now; return true
    }
    fun reset() {last = Long.MIN_VALUE}
}

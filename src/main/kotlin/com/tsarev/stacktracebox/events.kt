package com.tsarev.stacktracebox


sealed class TraceBoxEvent

/**
 * Some text that is captured from process listener.
 */
data class TextTraceBoxEvent(
    val text: String,
    val type: String,
) : TraceBoxEvent()

/**
 * Captured trace event.
 */
data class TraceTraceBoxEvent(
    val firstLine: FirstTraceLine,
    val otherLines: List<TraceLine>,
    val type: String,
    val time: Long,
    val other: Map<String, String>,
) : TraceBoxEvent() {
    val text by lazy { "${firstLine.text}\n${otherLines.joinToString(separator = "\n") { it.text }}" }
}

/**
 * Process listener start event.
 */
object ProcessStartTraceBoxEvent : TraceBoxEvent()

/**
 * Process listener end event.
 */
object ProcessEndTraceBoxEvent : TraceBoxEvent()

sealed class TraceLine(val text: String) {
    companion object {
        fun parseOrNull(text: String) =
            FirstTraceLine.parseOrNull(text)
                ?: CausedByTraceLine.parseOrNull(text)
                ?: AtTraceLine.parseOrNull(text)

        fun parse(text: String) = parseOrNull(text) ?: error("Trace line must match one of trace patterns: $text")
    }
}

class FirstTraceLine private constructor(
    text: String,
    matchResult: MatchResult
) : TraceLine(text) {
    companion object {
        val regex = "\\s*([^:]*(Exception|Throwable)[^:]*)(:.*)?".toRegex()
        fun parseOrNull(text: String) = regex.find(text)?.let { FirstTraceLine(text, it) }
        fun parse(text: String) = parseOrNull(text) ?: error("Trace line must match first trace pattern: $text")
    }

    val exception: String

    val exceptionText: String?

    init {
        val match = regex.find(text) ?: error("Do not create [${this::class.simpleName}] with not matching text")
        exception = match.groupValues[1]
        exceptionText = if (match.groupValues.size >= 4) match.groupValues[3].removePrefix(":").trim() else null
    }
}
class CausedByTraceLine private constructor(
    text: String,
    matchResult: MatchResult
) : TraceLine(text) {
    companion object {
        val regex = "\\s*Caused\\sby:\\s([^:]+)(:.+)?".toRegex()
        fun parseOrNull(text: String) = regex.find(text)?.let { CausedByTraceLine(text, it) }
    }

    val causeException: String

    val causeText: String?

    init {
        val match = regex.find(text) ?: error("Do not create [${this::class.simpleName}] with not matching text")
        causeException = match.groupValues[1]
        causeText = if (match.groupValues.size > 2) match.groupValues[2].removePrefix(":") else null
    }
}
class AtTraceLine private constructor(
    text: String,
    matchResult: MatchResult
) : TraceLine(text) {
    companion object {
        val regex = "[\\t\\s]*at\\s([^(]+)(\\(([^)]+)(:\\d+)\\)?)?".toRegex()
        fun parseOrNull(text: String) = regex.find(text)?.let { AtTraceLine(text, it) }
    }

    val methodName: String

    val className: String?

    val position: Int?

    init {
        val match = regex.find(text) ?: error("Do not create [${this::class.simpleName}] with not matching text")
        methodName = match.groupValues[1]
        className = if (match.groupValues.size >= 4) match.groupValues[3] else null
        position = if (match.groupValues.size >= 5) match.groupValues[4].removePrefix(":").toInt() else null
    }
}
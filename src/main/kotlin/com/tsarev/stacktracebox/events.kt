package com.tsarev.stacktracebox

import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient


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
    val firstLine: String,
    val otherLines: List<String>,
    val type: String,
    val time: Long,
    val other: Map<String, String>,
) : TraceBoxEvent() {
    @get:Transient
    val text by lazy { "$firstLine\n${otherLines.joinToString(separator = "\n") { it }}" }
}

/**
 * Process listener start event.
 */
object ProcessStartTraceBoxEvent : TraceBoxEvent()

/**
 * Process listener end event.
 */
object ProcessEndTraceBoxEvent : TraceBoxEvent()
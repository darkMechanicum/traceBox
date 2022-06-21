package com.tsarev.stacktracebox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


fun String.removeNewLines() = removeSuffix("\n").removePrefix("\n")
fun Flow<TraceBoxEvent>.removeBlankText() =
    map { if (it is TextTraceBoxEvent) it.copy(text = it.text.removeNewLines()) else it }
        .filter { it !is TextTraceBoxEvent || it.text.isNotBlank() }

/**
 * Transforms [TraceBoxEvent] by dividing input in lines and emiting them in bulk
 * if they look like exception stack trace.
 *
 * Lines are treated as exception stack trace if first line matches [FirstTraceLine.regex]
 * and other matches [AtTraceLine.regex] or [CausedByTraceLine.regex].
 *
 * Each event type is processes independently of others, since stdout and
 * stderr events can be mixed shaken.
 */
suspend fun Flow<TraceBoxEvent>.filterStackTraces() = flow {
    val isRecordingByType = ConcurrentHashMap<String, Boolean>()
    val aggregators = ConcurrentHashMap<String, ConcurrentLinkedQueue<TraceLine>>()

    fun aggregatorByType(type: String) = aggregators.computeIfAbsent(type) { ConcurrentLinkedQueue<TraceLine>() }

    // Line analyze and [TraceTraceBoxEvent] emission.
    suspend fun analyzeLine(line: String, type: String, emit: suspend (TraceBoxEvent) -> Unit) {
        if (isRecordingByType[type] == true) {
            val parsedLine = TraceLine.parseNonFirstLineOrNull(line)
            if (parsedLine != null) {
                aggregatorByType(type).add(parsedLine)
            } else {
                val usedAggregator = aggregatorByType(type)
                val firstTraceLine = usedAggregator.firstOrNull()
                isRecordingByType[type] = false
                if (firstTraceLine != null) {
                    val otherLines = usedAggregator.drop(1).toList()
                    usedAggregator.clear()
                    emit(
                        TraceTraceBoxEvent(
                            firstTraceLine as FirstTraceLine,
                            otherLines,
                            type,
                            System.currentTimeMillis(),
                            emptyMap(),
                        )
                    )
                    // Try second time, in case it is first line of new exception.
                    analyzeLine(line, type, emit)
                }
            }
        } else {
            val firstLine = FirstTraceLine.parseOrNull(line)
            if (firstLine != null) {
                isRecordingByType[type] = true
                aggregatorByType(type).add(firstLine)
            }
        }
    }

    // Actual transforming.
    removeBlankText().collect { event ->
        if (event !is TextTraceBoxEvent)
            emit(event)
        else {
            val type = event.type
            event.text.split('\n')
                .filter { it.isNotBlank() }
                .forEach { line -> analyzeLine(line, type) { emit(it) } }
        }
    }
}
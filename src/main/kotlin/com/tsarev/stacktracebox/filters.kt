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
 * Lines are treated as exception stack trace if first line matches [traceFirstLineRegex]
 * and other matches [atTraceLineRegex] or [traceCausedByLineRegex].
 *
 * Each event type is processes independently of others, since stdout and
 * stderr events can be mixed shaken.
 */
suspend fun Flow<TraceBoxEvent>.filterStackTraces() = flow {
    val isRecordingByType = ConcurrentHashMap<String, Boolean>()
    val aggregators = ConcurrentHashMap<String, ConcurrentLinkedQueue<TraceLine>>()
    fun aggregatorByType(type: String) = aggregators.computeIfAbsent(type) { ConcurrentLinkedQueue<TraceLine>() }
    removeBlankText().collect { event ->
        if (event !is TextTraceBoxEvent)
            emit(event)
        else {
            val type = event.type
            event.text.split('\n').forEach { line ->
                if (isRecordingByType[type] == true) {
                    val parsedLine = TraceLine.parseOrNull(line)
                    if (parsedLine != null) {
                        aggregatorByType(type).add(parsedLine)
                    } else {
                        val usedAggregator = aggregatorByType(type)
                        val firstTraceLine = usedAggregator.first()
                        val otherLines = usedAggregator.drop(1).toList()
                        usedAggregator.clear()
                        isRecordingByType[type] = false
                        emit(
                            TraceTraceBoxEvent(
                                firstTraceLine as FirstTraceLine,
                                otherLines,
                                type,
                                System.currentTimeMillis(),
                                emptyMap(),
                            )
                        )
                    }
                } else {
                    val firstLie = FirstTraceLine.parseOrNull(line)
                    if (firstLie != null) {
                        isRecordingByType[type] = true
                        aggregatorByType(type).add(firstLie)
                    }
                }
            }
        }
    }
}
package com.tsarev.stacktracebox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


val traceFirstLineRegex = ".*?(Exception|Throwable)[^:]*:.*".toRegex()
val String.isTraceFirstLine get() = matches(traceFirstLineRegex)

val traceLineRegex = "[\\t ]+at [^(]+\\([^)]+\\)?".toRegex()
val String.isTraceInnerLine get() = matches(traceLineRegex)

val traceCausedByLineRegex = "Caused by: .+".toRegex()
val String.isTraceCausedByLine get() = matches(traceCausedByLineRegex)


/**
 * Transforms [TraceBoxEvent] by dividing input in lines and emiting them in bulk
 * if they look like exception stack trace.
 *
 * Lines are treated as exception stack trace if first line matches [traceFirstLineRegex]
 * and other matches [traceLineRegex] or [traceCausedByLineRegex].
 *
 * Each event type is processes independently of others, since stdout and
 * stderr events can be mixed shaken.
 */
suspend fun Flow<TraceBoxEvent>.filterStackTraces() = flow {
    val isRecordingByType = ConcurrentHashMap<String, Boolean>()
    val aggregators = ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()
    fun aggregatorByType(type: String) = aggregators.computeIfAbsent(type) { ConcurrentLinkedQueue<String>() }
    collect { event ->
        if (event !is TextTraceBoxEvent)
            emit(event)
        else {
            val type = event.type
            event.text.split('\n').forEach { line ->
                if (isRecordingByType[type] == true) {
                    if (line.isTraceInnerLine || line.isTraceCausedByLine) aggregatorByType(type).add(line)
                    else {
                        val usedAggregator = aggregatorByType(type)
                        val firstTraceLine = usedAggregator.first()
                        val otherLines = usedAggregator.drop(1).toList()
                        usedAggregator.clear()
                        isRecordingByType[type] = false
                        emit(
                            TraceTraceBoxEvent(
                                firstTraceLine,
                                otherLines,
                                type,
                                System.currentTimeMillis(),
                                emptyMap(),
                            )
                        )
                    }
                } else {
                    if (line.isTraceFirstLine) {
                        isRecordingByType[type] = true
                        aggregatorByType(type).add(line)
                    }
                }
            }
        }
    }
}
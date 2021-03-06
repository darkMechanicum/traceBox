package com.tsarev.stacktracebox

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


@Service
class FilteredTraceEvents(
    project: Project
) : ServiceWithScope() {

    private val traceReplay = 500

    private val listenersRegistrar = project.service<ProcessListenersRegistrar>()

    @Suppress("RemoveExplicitTypeArguments")
    val traceFlow = channelFlow<TraceTraceBoxEvent> {
        listenersRegistrar.listenersFlow.collect { unfilteredFlow ->
            // TODO Add timeout to prevent leaking coroutines.
            myScope.launch collectingProcessLogs@{
                unfilteredFlow.filterStackTraces()
                    .collect {
                        when (it) {
                            is ProcessEndTraceBoxEvent -> this@collectingProcessLogs.cancel()
                            is TraceTraceBoxEvent -> send(it)
                            is TextTraceBoxEvent, ProcessStartTraceBoxEvent -> Unit // ignore
                        }
                    }
            }
        }
    }.shareIn(myScope, SharingStarted.Eagerly, traceReplay)

}


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
fun Flow<TraceBoxEvent>.filterStackTraces() = flow {
    val isRecordingByType = ConcurrentHashMap<String, Boolean>()
    val aggregators = ConcurrentHashMap<String, ConcurrentLinkedQueue<TraceLine>>()

    fun aggregatorByType(type: String) = aggregators.computeIfAbsent(type) { ConcurrentLinkedQueue<TraceLine>() }

    // Line analyze and [TraceTraceBoxEvent] emission.
    suspend fun analyzeLine(
        line: String,
        type: String,
        runDescName: String,
        emit: suspend (TraceBoxEvent) -> Unit
    ) {
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
                            mapOf(
                                runDescriptorNameProp to runDescName
                            ),
                        )
                    )
                    // Try second time, in case it is first line of new exception.
                    analyzeLine(line, type, runDescName, emit)
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
                .forEach { line -> analyzeLine(line, type, event.processName) { emit(it) } }
        }
    }
}
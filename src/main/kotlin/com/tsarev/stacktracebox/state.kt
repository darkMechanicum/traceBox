package com.tsarev.stacktracebox

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.tsarev.stacktracebox.ui.TraceBoxToolWindowFactory
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service to persist captured traces.
 */
@Service
@State(name = "com.tsarev.tracebox.traces")
class TraceBoxStateHolder(
    private val project: Project
) : PersistentStateComponent<TraceBoxStateHolder.State>, Disposable {

    val traceEventsQueue = ConcurrentLinkedQueue<TraceTraceBoxEvent>()

    private val collectorsScope = CoroutineScope(Job())

    // TODO Rework state persisting - there should be way not to
    // TODO duplicate [TraceTraceBoxEvent] class.
    override fun getState() = State().apply {
        val traces = traceEventsQueue.map {
            TraceTraceBoxEventStateAdapter().apply {
                firstLine = it.firstLine.text
                otherLines = it.otherLines.map { it.text }
                type = it.type
                time = it.time
                other = it.other
            }
        }
        storedExceptions = traces
    }

    override fun loadState(state: State): Unit = with(traceEventsQueue) {
        clear()
        state.storedExceptions?.forEach {
            add(
                TraceTraceBoxEvent(
                    firstLine = FirstTraceLine.parse(it.firstLine!!),
                    otherLines = it.otherLines?.map { TraceLine.parse(it) } ?: emptyList(),
                    type = it.type!!,
                    time = it.time!!,
                    other = it.other ?: emptyMap(),
                )
            )
        }
    }.also {
        TraceBoxToolWindowFactory.reloadAll(project)
    } ?: Unit

    override fun initializeComponent() {
        val listenersRegistrar = project.service<ProcessListenersRegistrar>()
        collectorsScope.launch { listenersRegistrar.doCollectLogs() }
    }

    private suspend fun ProcessListenersRegistrar.doCollectLogs() {
        listenersFlow.collect { flow ->
            coroutineScope {
                launch collectingProcessLogs@{
                    flow.filterStackTraces()
                        .collect {
                            when (it) {
                                is ProcessEndTraceBoxEvent -> this@collectingProcessLogs.cancel()
                                is TraceTraceBoxEvent -> traceEventsQueue.add(it)
                                is TextTraceBoxEvent -> Unit // ignore
                                is ProcessStartTraceBoxEvent -> Unit // ignore
                            }
                        }
                }
            }
        }
    }

    class State {
        var storedExceptions: List<TraceTraceBoxEventStateAdapter>? = null
    }

    class TraceTraceBoxEventStateAdapter {
        var firstLine: String? = null
        var otherLines: List<String>? = null
        var type: String? = null
        var time: Long? = null
        var other: Map<String, String>? = null
    }

    override fun dispose() {
        collectorsScope.cancel()
    }

}
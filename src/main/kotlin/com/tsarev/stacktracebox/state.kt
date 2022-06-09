package com.tsarev.stacktracebox

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

@Service
@State(name = "com.tsarev.tracebox.traces")
class TraceBoxStateHolder(
    val project: Project
) : PersistentStateComponent<TraceBoxStateHolder.State>, Disposable {

    val traceEventsQueue = ConcurrentLinkedQueue<TraceTraceBoxEvent>()

    val collectorsScope = CoroutineScope(Job())

    override fun getState() = State(traceEventsQueue.toList())

    override fun loadState(state: State): Unit = with(traceEventsQueue) {
        clear()
        addAll(state.storedExceptions)
    }

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

    data class State(
        val storedExceptions: List<TraceTraceBoxEvent>
    )

    override fun dispose() {
        collectorsScope.cancel()
    }

}
package com.tsarev.stacktracebox

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.tsarev.stacktracebox.ui.TraceBoxToolWindowFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service to persist captured traces.
 */
@Service
@State(name = "com.tsarev.tracebox.traces")
class TraceBoxStateManager(
    private val project: Project
) : PersistentStateComponent<TraceBoxStateManager.State>, ScopeAwareDisposable {

    val traceEventsQueue = ConcurrentLinkedQueue<TraceTraceBoxEvent>()

    override val myScope = CoroutineScope(Job())

    private val navigation = project.service<NavigationCalculationService>()

    private val filteredTraces = project.service<FilteredTraceEvents>()

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

    override fun loadState(state: State) {
        traceEventsQueue.clear()
        state.storedExceptions?.mapTo(traceEventsQueue) {
            TraceTraceBoxEvent(
                firstLine = FirstTraceLine.parse(it.firstLine!!),
                otherLines = it.otherLines?.map { TraceLine.parse(it) } ?: emptyList(),
                type = it.type!!,
                time = it.time!!,
                other = it.other ?: emptyMap(),
            )
        }
        traceEventsQueue.forEach { navigation.scheduleCalculateNavigation(it) }
        TraceBoxToolWindowFactory.reloadAll(project)
    }

    override fun initializeComponent() {
        myScope.launch {
            filteredTraces.traceFlow.collect { traceEventsQueue.add(it) }
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
}
package com.tsarev.stacktracebox

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import com.tsarev.stacktracebox.ui.TraceBoxToolWindowFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service to persist captured traces.
 */
@Service
@Storage("traceBox.xml")
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
        state.storedExceptions?.mapNotNull {
            val firstLine = FirstTraceLine.parseOrNull(it.firstLine!!)
            if (firstLine != null) {
                TraceTraceBoxEvent(
                        firstLine = firstLine,
                        otherLines = it.otherLines?.map { TraceLine.parse(it) } ?: emptyList(),
                        type = it.type!!,
                        time = it.time!!,
                ).apply {
                    it.other?.forEach { (key, value) -> this[key] = value }
                }
            } else null
        }?.mapTo(traceEventsQueue) { it }
        runBlocking {
            traceEventsQueue.forEach { navigation.scheduleCalculateNavigation(it) }
        }
        TraceBoxToolWindowFactory.reloadAll(project)
    }

    override fun initializeComponent() {
        myScope.launch {
            filteredTraces.traceFlow.collect { traceEventsQueue.add(it) }
        }
    }

    class State {
        @XCollection
        var storedExceptions: List<TraceTraceBoxEventStateAdapter>? = null
    }

    class TraceTraceBoxEventStateAdapter {
        @Attribute
        var firstLine: String? = null
        @XCollection
        var otherLines: List<String>? = null
        @Attribute
        var type: String? = null
        @Attribute
        var time: Long? = null
        @XMap
        var other: Map<String, String>? = null
    }
}
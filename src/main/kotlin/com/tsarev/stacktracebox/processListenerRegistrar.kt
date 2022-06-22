package com.tsarev.stacktracebox

import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.EmptyCoroutineContext


private const val lookFrequency = 500L

private const val listenersReplay = 10

private const val eventsReplay = 500

/**
 * Looks for currently running processes via [ExecutionManager.getRunningProcesses]
 * every [lookFrequency] ms and register listener for each found one.
 *
 * Emits a shared flow with created listeners, that is stopped after service disposal.
 */
@Service
class ProcessListenersRegistrar(
    project: Project
) : Disposable, DumbAware {

    private val executionManager = ExecutionManager.getInstance(project)

    private val managedProcesses = ConcurrentHashMap<ProcessHandler, Boolean>()

    private val listenersFlowScope = CoroutineScope(EmptyCoroutineContext)

    // This flow must be shared - since process executions can be created
    // independently of their subscribing and listening.
    val listenersFlow = flow {
        while (true) {
            val runningProcesses = executionManager.getRunningProcesses()
            runningProcesses
                .filter { !it.isProcessTerminated }
                .forEach { ph ->
                    if (!managedProcesses.containsKey(ph)) {
                        managedProcesses[ph] = true
                        val listener = LogProcessListener(ph, currentCoroutineContext().job) {
                            managedProcesses.remove(ph)
                        }
                        ph.addProcessListener(listener, this@ProcessListenersRegistrar)
                        emit(listener.listenerFlow)
                    }
                }
            delay(lookFrequency)
        }
    }.shareIn(listenersFlowScope, SharingStarted.Eagerly, listenersReplay)

    override fun dispose() {
        listenersFlowScope.cancel()
        managedProcesses.clear()
    }
}

/**
 * Listens to [ProcessHandler] and emits collected messages as shared flow.
 * This listener scope require parent job to support structured cancellability.
 *  - Sends [ProcessStartTraceBoxEvent] after init.
 *  - Listener removes itself, stops shared flow [listenerScope] and sends [ProcessEndTraceBoxEvent]
 * after listened process is terminated.
 */
class LogProcessListener(
    private val ph: ProcessHandler,
    parentJob: Job,
    private val onTerminate: () -> Unit,
) : ProcessAdapter() {

    private val eventsFlow: MutableSharedFlow<TraceBoxEvent> = MutableSharedFlow(replay = eventsReplay)

    private val listenerScope = CoroutineScope(Job(parentJob))

    // This flow must be shared - since process listening can be independent of
    // its output processing.
    val listenerFlow = eventsFlow
        .shareIn(listenerScope, SharingStarted.Eagerly)

    init {
        runBlocking {
            eventsFlow.emit(ProcessStartTraceBoxEvent)
        }
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) = runBlocking {
        val trimmedText = event.text.removePrefix("\n").removeSuffix("\n")
        val textEvent = TextTraceBoxEvent(
            trimmedText,
            outputType.toString().intern(),
        )
        eventsFlow.emit(textEvent)
    }.run { }

    override fun processTerminated(event: ProcessEvent) = runBlocking {
        ph.removeProcessListener(this@LogProcessListener)
        eventsFlow.emit(ProcessEndTraceBoxEvent)
        listenerScope.cancel()
        onTerminate()
    }
}
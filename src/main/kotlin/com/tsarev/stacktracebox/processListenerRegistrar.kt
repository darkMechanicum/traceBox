package com.tsarev.stacktracebox

import com.intellij.execution.ExecutionManager
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.SmartList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap


private const val lookFrequency = 50L

private const val listenersReplay = 10

private const val eventsReplay = 500

typealias ProcessHandlerEx = Pair<RunContentDescriptor, ProcessHandler>

/**
 * Looks for currently running processes via [ExecutionManager.getRunningProcesses]
 * every [lookFrequency] ms and register listener for each found one.
 *
 * Emits a shared flow with created listeners, that is stopped after service disposal.
 */
@Service
class ProcessListenersRegistrar(
    private val project: Project
) : ServiceWithScope(), DumbAware {

    private val managedProcesses = ConcurrentHashMap<ProcessHandler, Boolean>()

    // This flow must be shared - since process executions can be created
    // independently of their subscribing and listening.
    val listenersFlow = flow {
        while (true) {
            val runningProcesses = getRunningProcesses()
            runningProcesses
                .filter { !it.second.isProcessTerminated }
                .forEach { (runDesc, ph) ->
                    if (!managedProcesses.containsKey(ph)) {
                        managedProcesses[ph] = true
                        val listener = LogProcessListener(runDesc, ph, currentCoroutineContext().job) {
                            managedProcesses.remove(ph)
                        }
                        println("NEW LISTENER FOR PROCESS! ${runDesc.displayName}")
                        ph.addProcessListener(listener, this@ProcessListenersRegistrar)
                        emit(listener)
                    }
                }
            delay(lookFrequency)
        }
    }.shareIn(myScope, SharingStarted.Eagerly, listenersReplay)

    private val emptyProcessHandlers = arrayOf<ProcessHandlerEx>()

    private fun getRunningProcesses(): Array<ProcessHandlerEx> {
        var handlers: MutableList<ProcessHandlerEx>? = null
        for (descriptor in ExecutionManagerImpl.getAllDescriptors(project)) {
            val processHandler = descriptor.processHandler ?: continue
            if (handlers == null) {
                handlers = SmartList()
            }
            handlers.add(descriptor to processHandler)
        }
        return handlers?.toTypedArray() ?: emptyProcessHandlers
    }

    override fun dispose() {
        super.dispose()
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
    private val runContentDescriptor: RunContentDescriptor,
    private val ph: ProcessHandler,
    parentJob: Job,
    private val onTerminate: () -> Unit,
) : ProcessAdapter() {

    val processName get() = runContentDescriptor.displayName

    private val myEventsFlow: MutableSharedFlow<TraceBoxEvent> = MutableSharedFlow(replay = eventsReplay)

    private val listenerScope = CoroutineScope(Job(parentJob))

    // This flow must be shared - since process listening can be independent of
    // its output processing.
    val eventsFlow = myEventsFlow
        .shareIn(listenerScope, SharingStarted.Eagerly)

    init {
        runBlocking {
            myEventsFlow.emit(ProcessStartTraceBoxEvent)
        }
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) = runBlocking {
        val trimmedText = event.text.removePrefix("\n").removeSuffix("\n")
        val textEvent = TextTraceBoxEvent(
            trimmedText,
            outputType.toString().intern(),
        )
        myEventsFlow.emit(textEvent)
        println("NEW TEXT! $trimmedText")
    }.run { }

    override fun processTerminated(event: ProcessEvent) = runBlocking {
        ph.removeProcessListener(this@LogProcessListener)
        myEventsFlow.emit(ProcessEndTraceBoxEvent)
        listenerScope.cancel()
        onTerminate()
    }
}
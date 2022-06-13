package com.tsarev.stacktracebox.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.tsarev.stacktracebox.ClearTracesAction
import com.tsarev.stacktracebox.ProcessListenersRegistrar
import com.tsarev.stacktracebox.TraceBoxStateHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce

/**
 * Main Tracebox tool window panel.
 */
class TraceBoxPanel(
    private val project: Project
) : SimpleToolWindowPanel(true), Disposable {

    private val application = ApplicationManager.getApplication()

    private val myTreeStructure = CollectTracesTreeStructure(project)

    private val myStructureTreeModel = StructureTreeModel(myTreeStructure, project)

    private val myTree = Tree().apply {
        isRootVisible = true
        model = AsyncTreeModel(myStructureTreeModel, project)
    }

    private val listenerRegistrar = project.service<ProcessListenersRegistrar>()

    private val stateHolder = project.service<TraceBoxStateHolder>()

    @OptIn(DelicateCoroutinesApi::class, FlowPreview::class)
    private val job = GlobalScope.launch {
        coroutineScope {
            listenerRegistrar.listenersFlow.collect { flow ->
                launch collectingProcessLogs@{
                    flow.debounce(500).collect {
                        reloadTraces()
                    }
                }
            }
        }
    }

    init {
        setContent(myTree)
        val actionGroup = DefaultActionGroup(
            "TraceBoxActionGroup",
            listOf(ClearTracesAction)
        )
        toolbar = ActionToolbarImpl("TraceBoxToolbar", actionGroup, false)
        reloadTraces()
    }

    override fun dispose() = job.cancel()

    fun reloadTraces() = with(myTreeStructure) {
        traces.clear()
        traces.addAll(stateHolder.traceEventsQueue.toList().map {
            it.text.lines().first() to it.text.lines().drop(1)
                .joinToString(separator = "\n") { it }
        })
        application.invokeLater {
            myStructureTreeModel.invalidate()
        }
    }
}
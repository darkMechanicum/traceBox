package com.tsarev.stacktracebox

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.tsarev.stacktracebox.ui.CollectTracesTreeStructure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce


class CollectLogsToolWindowFactory : ToolWindowFactory, DumbAware {

    private val myTree = Tree()

    private var myStructureTreeModel: StructureTreeModel<CollectTracesTreeStructure>? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(project.createContent())
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun Project.createContent(): Content {
        val myTreeStructure = CollectTracesTreeStructure(this)
        myTree.isRootVisible = true
        myStructureTreeModel = StructureTreeModel(myTreeStructure, this)
        myTree.model = AsyncTreeModel(myStructureTreeModel!!, this)

        val listenerRegistrar = service<ProcessListenersRegistrar>()
        val stateHolder = service<TraceBoxStateHolder>()
        val job = GlobalScope.launch {
            doListenForUpdates(myTreeStructure, myStructureTreeModel!!, listenerRegistrar, stateHolder)
        }
        val content = ContentImpl(
            myTree, "Traces", true
        )
        content.setDisposer { job.cancel() }
        return content
    }

    @OptIn(FlowPreview::class)
    private suspend fun doListenForUpdates(
        model: CollectTracesTreeStructure,
        structureTreeModel: StructureTreeModel<CollectTracesTreeStructure>,
        listenerRegistrar: ProcessListenersRegistrar,
        stateHolder: TraceBoxStateHolder,
    ) {
        coroutineScope {
            listenerRegistrar.listenersFlow.collect { flow ->
                launch collectingProcessLogs@{
                    flow.debounce(500).collect {
                        model.traces.clear()
                        model.traces.addAll(stateHolder.traceEventsQueue.toList().map {
                            it.text.lines().first() to it.text.lines().drop(1)
                                .joinToString(separator = "\n") { it }
                        })
                        structureTreeModel.invalidate()
                    }
                }
            }
        }
    }
}
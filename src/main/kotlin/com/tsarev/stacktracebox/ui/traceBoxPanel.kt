package com.tsarev.stacktracebox.ui

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.tsarev.stacktracebox.ClearTracesAction
import com.tsarev.stacktracebox.ProcessListenersRegistrar
import com.tsarev.stacktracebox.TraceBoxStateHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Main Tracebox tool window panel.
 */
class TraceBoxPanel(
    private val project: Project
) : SimpleToolWindowPanel(true), Disposable {

    private val application = ApplicationManager.getApplication()

    private val stateHolder = project.service<TraceBoxStateHolder>()

    private val myTreeStructure = CollectTracesTreeStructure(project, stateHolder)

    private val myStructureTreeModel = StructureTreeModel(myTreeStructure, project)

    private var myAutoScrollMode = true

    private val myAutoScrollToSource = object : AutoScrollToSourceHandler() {
        override fun isAutoScrollMode() = myAutoScrollMode
        override fun setAutoScrollMode(state: Boolean) {
            myAutoScrollMode = state
        }
    }

    private val myActionGroup = DefaultActionGroup(
        "TraceBoxActionGroup",
        listOf(
            ClearTracesAction,
            myAutoScrollToSource.createToggleAction()
        )
    )

    private val myToolbar = ActionToolbarImpl("TraceBoxToolbar", myActionGroup, false)

    private val myTree = Tree().apply {
        isRootVisible = false
        model = AsyncTreeModel(myStructureTreeModel, project)
        myAutoScrollToSource.install(this)
    }

    private val listenerRegistrar = project.service<ProcessListenersRegistrar>()

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
        myToolbar.targetComponent = myTree
        toolbar = myToolbar
        reloadTraces()
    }

    override fun dispose() = job.cancel()

    fun reloadTraces() {
        application.invokeLater {
            myStructureTreeModel.invalidate()
        }
    }

    override fun getData(dataId: String) = when {
        CommonDataKeys.NAVIGATABLE.`is`(dataId) ->
            myTree.selectedNode?.managedLine?.getNavigatableCached(project)
        CommonDataKeys.VIRTUAL_FILE.`is`(dataId) ->
            myTree.selectedNode?.managedLine?.getPsiFileCached(project)?.virtualFile
        CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) ->
            myTree.selectedNode?.managedLine?.getPsiFileCached(project)?.virtualFile
                ?.let { arrayOf(it) } ?: VirtualFile.EMPTY_ARRAY
        else -> super.getData(dataId)
    }

    val Tree.selectedNode: BaseTraceNode? get() = selectionPath
        ?.lastPathComponent
        ?.tryCast<DefaultMutableTreeNode>()
        ?.userObject
        ?.tryCast<NodeDescriptor<*>>()
        ?.element
        ?.tryCast<BaseTraceNode>()

    inline fun <reified T> Any?.tryCast() = this as? T
}
package com.tsarev.stacktracebox.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.tsarev.stacktracebox.ClearTracesAction
import com.tsarev.stacktracebox.NavigationCalculationService
import com.tsarev.stacktracebox.ProcessListenersRegistrar
import com.tsarev.stacktracebox.TraceBoxStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import java.awt.datatransfer.StringSelection
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Main Tracebox tool window panel.
 */
class TraceBoxPanel(
    private val project: Project
) : SimpleToolWindowPanel(true), Disposable {

    private val application = ApplicationManager.getApplication()

    private val stateHolder = project.service<TraceBoxStateManager>()

    private val myTreeStructure = CollectTracesTreeStructure(project, stateHolder)

    private val myStructureTreeModel = StructureTreeModel(myTreeStructure, project)

    private var myAutoScrollMode = true

    private val myAutoScrollToSource = object : AutoScrollToSourceHandler() {
        override fun isAutoScrollMode() = myAutoScrollMode
        override fun setAutoScrollMode(state: Boolean) {
            myAutoScrollMode = state
        }
    }

    private val myToolbarActionGroup = DefaultActionGroup(
        "TraceBoxToolbarActionGroup",
        listOf(
            ClearTracesAction,
            myAutoScrollToSource.createToggleAction()
        )
    )

    private val copyToClipboardAction = object : AnAction(
        "Copy",
        "Copies current trace to clipboard",
        AllIcons.Actions.Copy
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val text = myTree.selectedNode?.value?.text
            if (text != null) {
                CopyPasteManager.getInstance().setContents(StringSelection(text))
            }
        }
    }

    private val myContextActionGroup: DefaultActionGroup = DefaultActionGroup(
        "TraceBoxContextActionGroup",
        listOf(
            ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE),
            copyToClipboardAction
        )
    )

    private val myToolbar = ActionToolbarImpl("TraceBoxToolbar", myToolbarActionGroup, false)

    private val myTree = Tree().apply {
        isRootVisible = false
        model = AsyncTreeModel(myStructureTreeModel, project)
        myAutoScrollToSource.install(this)
        PopupHandler.installPopupMenu(
            this,
            myContextActionGroup,
            "TraceBoxPopupMenu",
        )
    }

    private val listenerRegistrar = project.service<ProcessListenersRegistrar>()

    @OptIn(DelicateCoroutinesApi::class, FlowPreview::class)
    private val onNewTracesJob = GlobalScope.launch {
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

    private val navigation = project.service<NavigationCalculationService>()

    @OptIn(DelicateCoroutinesApi::class, FlowPreview::class)
    private val onNavigationRecalcJob = GlobalScope.launch {
        navigation.recalculatedFlow.debounce(500).collect {
            reloadTraces()
        }
    }

    init {
        setContent(ScrollPaneFactory.createScrollPane(myTree))
        myToolbar.targetComponent = myTree
        toolbar = myToolbar
        reloadTraces()
    }

    override fun dispose() {
        onNewTracesJob.cancel()
        onNavigationRecalcJob.cancel()
    }

    fun reloadTraces() {
        application.invokeLater {
            myStructureTreeModel.invalidate()
        }
    }

    override fun getData(dataId: String) = when {
        CommonDataKeys.NAVIGATABLE.`is`(dataId) -> {
            myTree.selectedNode?.managedLine?.navigatable
        }

        CommonDataKeys.VIRTUAL_FILE.`is`(dataId) ->
            myTree.selectedNode?.managedLine?.element?.containingFile?.virtualFile

        CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) ->
            myTree.selectedNode?.managedLine?.element?.containingFile?.virtualFile
                ?.let { arrayOf(it) } ?: VirtualFile.EMPTY_ARRAY

        else -> super.getData(dataId)
    }

    private val Tree.selectedNode: BaseTraceNode?
        get() = selectionPath
            ?.lastPathComponent
            ?.tryCast<DefaultMutableTreeNode>()
            ?.userObject
            ?.tryCast<NodeDescriptor<*>>()
            ?.element
            ?.tryCast<BaseTraceNode>()

    private inline fun <reified T> Any?.tryCast() = this as? T
}
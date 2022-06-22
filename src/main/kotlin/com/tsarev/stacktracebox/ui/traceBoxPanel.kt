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
@OptIn(FlowPreview::class)
class TraceBoxPanel(
    private val project: Project
) : SimpleToolWindowPanel(true), Disposable {

    private val application = ApplicationManager.getApplication()

    private val myStateManager = project.service<TraceBoxStateManager>()

    private val myTreeStructure = CollectTracesTreeStructure(project, myStateManager)

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

    private val myCopyToClipboardAction = object : AnAction(
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
            myCopyToClipboardAction
        )
    )

    private val myToolbar = ActionToolbarImpl(
        "TraceBoxToolbar",
        myToolbarActionGroup,
        false
    )

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

    private val myListenerRegistrar = project.service<ProcessListenersRegistrar>()

    private val myScope = CoroutineScope(Job())

    private val myNavigation = project.service<NavigationCalculationService>()

    init {
        setContent(ScrollPaneFactory.createScrollPane(myTree))
        myToolbar.targetComponent = myTree
        toolbar = myToolbar
        reloadTraces()

        // Watch on new traces to reload.
        myScope.launch(Job()) {
            coroutineScope {
                myListenerRegistrar.listenersFlow.collect { flow ->
                    launch collectingProcessLogs@{
                        flow.debounce(500).collect {
                            reloadTraces()
                        }
                    }
                }
            }
        }

        // Watch on navigation info to reload.
        myScope.launch(Job()) {
            myNavigation.recalculatedFlow.debounce(500).collect {
                reloadTraces()
            }
        }
    }

    override fun dispose() {
        myScope.cancel()
    }

    /**
     * Invalidate tree and reload traces from [myStateManager].
     */
    fun reloadTraces() {
        application.invokeLater {
            myStructureTreeModel.invalidate()
        }
    }

    override fun getData(dataId: String) = when {
        // Support for navigation logic.
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
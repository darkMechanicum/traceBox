package com.tsarev.stacktracebox.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.tsarev.stacktracebox.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce

/**
 * Main Tracebox tool window panel.
 */
@OptIn(FlowPreview::class)
class TraceBoxPanel(
    private val project: Project
) : SimpleToolWindowPanel(true), ScopeAwareDisposable {

    private val application = ApplicationManager.getApplication()

    private val myStateManager = project.service<TraceBoxStateManager>()

    private val myTreeStructure = CollectTracesTreeStructure(project, myStateManager, this)

    private val myStructureTreeModel = StructureTreeModel(myTreeStructure, project)

    private var myAutoScrollMode = true

    private val myAutoScrollToSource = object : AutoScrollToSourceHandler() {
        init {
            isAutoScrollMode = false
        }

        override fun isAutoScrollMode() = myAutoScrollMode
        override fun setAutoScrollMode(state: Boolean) {
            myAutoScrollMode = state
        }
    }

    private val availableGroupingCriteria = listOf(
        GroupByFirstLine,
        GroupByExceptionClass,
        *GROUP_BY_CRITERIA_EP.getExtensions(project)
    )

    private val groupingActions = availableGroupingCriteria
        .map { it.createToggleAction { reloadTraces() } }

    internal val myGrouping
        get() = groupingActions
            .filter { it.state }
            .map { it.criteria }
            .sortedBy { it.priority }

    private val myTree = Tree()

    private val myToolbarActionGroup = DefaultActionGroup().apply {
        add(ClearTracesAction)
        add(myAutoScrollToSource.createToggleAction())
        addSeparator()
        add(ExpandAll(myTree))
        add(CollapseAll(myTree))
        addSeparator()
        addAll(groupingActions)
    }

    private val myToolbar = ActionToolbarImpl(
        "TraceBoxToolbar",
        myToolbarActionGroup,
        false
    )

    private val myCopyToClipboardAction = CopyTraceToClipboardAction(myTree)

    private val myAnalyzeTraceAction = AnalyzeTraceAction(myTree)

    private val myContextActionGroup = DefaultActionGroup().apply {
        add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE))
        addSeparator()
        add(myCopyToClipboardAction)
        add(myAnalyzeTraceAction)
        addSeparator()
        add(SearchInWebAction(myTree))
    }

    private val myFilteredTraces = project.service<FilteredTraceEvents>()

    override val myScope = CoroutineScope(Job())

    init {
        setContent(ScrollPaneFactory.createScrollPane(myTree))
        myToolbar.targetComponent = myTree
        toolbar = myToolbar
        reloadTraces()

        // Watch on new traces to reload.
        myScope.launch(Job()) {
            coroutineScope {
                myFilteredTraces.traceFlow.debounce(500).collect {
                    reloadTraces()
                }
            }
        }

        // Init tree
        myTree.apply {
            isRootVisible = false
            model = AsyncTreeModel(myStructureTreeModel, project)
            myAutoScrollToSource.install(this)
            PopupHandler.installPopupMenu(
                this,
                myContextActionGroup,
                "TraceBoxPopupMenu",
            )
        }
    }

    /**
     * Invalidate tree and reload traces from [myStateManager].
     */
    fun reloadTraces() {
        DumbService.getInstance(project).smartInvokeLater {
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
}
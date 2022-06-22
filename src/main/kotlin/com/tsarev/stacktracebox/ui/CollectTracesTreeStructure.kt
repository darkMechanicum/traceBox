package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.project.Project
import com.tsarev.stacktracebox.TraceBoxStateManager
import com.tsarev.stacktracebox.TraceTraceBoxEvent

/**
 * Tree structure, that delegates to [TraceBoxStateManager] to retrieve traces.
 */
class CollectTracesTreeStructure(
    private val project: Project,
    private val stateHolder: TraceBoxStateManager,
    private val myPanel: TraceBoxPanel
) : AbstractTreeStructureBase(project) {

    private val rootNode = RootTraceNode(project)
    override fun getRootElement() = rootNode
    override fun commit() = Unit
    override fun hasSomethingToCommit() = false
    override fun getProviders() = mutableListOf<TreeStructureProvider>()

    @Suppress("SENSELESS_COMPARISON")
    override fun getChildElements(element: Any): Array<out BaseTraceNode> {
        return if (element == null || element === rootNode) {
            tryGroup(stateHolder.traceEventsQueue, myPanel.myGrouping).toTypedArray()
        } else if (element is WholeTraceNode) {
            element.children.toTypedArray()
        } else if (element is AbstractTreeNode<*>) {
            (element.children as MutableCollection<out BaseTraceNode>).toTypedArray()
        } else emptyArray()
    }

    private fun tryGroup(
        traces: Collection<TraceTraceBoxEvent>,
        groupByCriterias: List<GroupByCriteria>
    ): MutableCollection<BaseTraceNode> {
        val firstCriteria = groupByCriterias.firstOrNull()
            ?: return traces.mapTo(mutableListOf()) { WholeTraceNode(project, it) }
        val otherCriterias = groupByCriterias.drop(1)
        val grouped = firstCriteria.group(traces).entries
        return grouped.mapTo(mutableListOf()) {
            GroupByNode(
                project,
                it.key,
                tryGroup(it.value, otherCriterias)
            )
        }
    }
}
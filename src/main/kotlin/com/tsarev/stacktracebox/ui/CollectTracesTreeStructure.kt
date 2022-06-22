package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.project.Project
import com.tsarev.stacktracebox.TraceBoxStateManager

/**
 * Tree structure, that delegates to [TraceBoxStateManager] to retrieve traces.
 */
class CollectTracesTreeStructure(
    private val project: Project,
    private val stateHolder: TraceBoxStateManager
) : AbstractTreeStructureBase(project) {

    private val rootNode = RootTraceNode(project)
    override fun getRootElement() = rootNode
    override fun commit() = Unit
    override fun hasSomethingToCommit() = false
    override fun getProviders() = mutableListOf<TreeStructureProvider>()

    @Suppress("SENSELESS_COMPARISON")
    override fun getChildElements(element: Any): Array<out BaseTraceNode> {
        return if (element is WholeTraceNode) {
            element.children.toTypedArray()
        } else if (element == null || element === rootNode)
            stateHolder
                .traceEventsQueue.map { WholeTraceNode(project, it) }
                .toTypedArray()
        else emptyArray()
    }
}
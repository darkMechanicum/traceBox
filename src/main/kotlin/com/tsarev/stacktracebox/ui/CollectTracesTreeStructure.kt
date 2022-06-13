package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.project.Project

class CollectTracesTreeStructure(
    private val project: Project
) : AbstractTreeStructureBase(project) {
    val traces = mutableListOf<Pair<String, String>>()
    private val rootNode = RootTraceNode(project)
    override fun getRootElement() = rootNode
    override fun commit() = Unit
    override fun hasSomethingToCommit() = false
    override fun getProviders() = mutableListOf<TreeStructureProvider>()

    @Suppress("SENSELESS_COMPARISON")
    override fun getChildElements(element: Any): Array<out BaseTraceNode> {
        return if (element is ExpandableTraceNode) {
            element.children.toTypedArray()
        } else if (element == null || element === rootNode) traces.mapIndexed { index, it ->
            ExpandableTraceNode(project, "$index ${it.first}", it.second)
        }.toTypedArray()
        else emptyArray()
    }
}
package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes

sealed class BaseTraceNode(project: Project, value: String) :
    AbstractTreeNode<String>(project, value) {

    override fun update(presentation: PresentationData) =
        presentation.addText(value, SimpleTextAttributes.REGULAR_ATTRIBUTES)

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf<ExpandableTraceNode>()
    }
}

class RootTraceNode(project: Project) : BaseTraceNode(project, "root")

class ExpandedTraceNode(project: Project, value: String) : BaseTraceNode(project, value)

class ExpandableTraceNode(
    project: Project,
    headerValue: String,
    private val allTraceValue: String,
) : BaseTraceNode(project, headerValue) {
    override fun getChildren() =
        allTraceValue
            .split("\n")
            .map { ExpandedTraceNode(project!!, it) }
            .toMutableList()
}
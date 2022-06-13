package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.tsarev.stacktracebox.TraceTraceBoxEvent

sealed class BaseTraceNode(
    project: Project,
    value: TraceTraceBoxEvent
) : AbstractTreeNode<TraceTraceBoxEvent>(project, value) {
    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf<ExpandableTraceNode>()
    }
}

val rootDummy = TraceTraceBoxEvent("dummy", emptyList(), "dummy", 0, emptyMap())

class RootTraceNode(project: Project) : BaseTraceNode(project, rootDummy) {
    override fun update(presentation: PresentationData) {
        // no-op
    }
}

class ExpandedTraceNode(
    project: Project,
    value: TraceTraceBoxEvent,
    val traceLineIndex: Int
) : BaseTraceNode(project, value) {
    override fun update(presentation: PresentationData) {
        presentation.addText(
            value.otherLines[traceLineIndex],
            SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES
        )
    }
}

class ExpandableTraceNode(
    project: Project,
    value: TraceTraceBoxEvent,
) : BaseTraceNode(project, value) {
    override fun getChildren(): MutableCollection<ExpandedTraceNode> =
        List(value.otherLines.size) { index -> ExpandedTraceNode(project!!, value, index) }
            .toMutableList()

    override fun update(presentation: PresentationData) = with(presentation) {
        addText("[${value.time}] ", SimpleTextAttributes.GRAY_ATTRIBUTES)
        addText(value.firstLine, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

}
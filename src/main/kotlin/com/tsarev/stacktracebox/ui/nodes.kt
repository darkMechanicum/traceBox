package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.tsarev.stacktracebox.*

sealed class BaseTraceNode(
    project: Project,
    value: TraceTraceBoxEvent
) : AbstractTreeNode<TraceTraceBoxEvent>(project, value) {

    open val managedLine: TraceLine? get() = null

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf<ExpandableTraceNode>()
    }
}

val rootDummy = TraceTraceBoxEvent(
    FirstTraceLine.parse("Exception"),
    emptyList(),
    "dummy",
    0,
    emptyMap()
)

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

    override val managedLine get() = value.otherLines[traceLineIndex]

    override fun update(presentation: PresentationData) = when (val line = value.otherLines[traceLineIndex]) {
        is CausedByTraceLine -> {
            presentation.addText("Caused by", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
            presentation.addText(line.causeException, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            if (line.causeText != null) {
                presentation.addText(": ", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                presentation.addText(line.causeText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            } else Unit
        }

        is AtTraceLine -> {
            @Suppress("DialogTitleCapitalization")
            presentation.addText("at ", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
            presentation.addText(line.methodName, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
            if (line.classSimpleName != null) {
                presentation.addText("(", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                presentation.addText(line.classSimpleName, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                if (line.position != null) {
                    presentation.addText(":${line.position}", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                }
                presentation.addText(")", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            } else Unit
        }

        else -> Unit
    }

    // Equals and hasCode override is added to workaround [AsyncTreeModel] duplicates removal.
    override fun equals(other: Any?) =
        this === other ||
                javaClass == other?.javaClass &&
                super.equals(other) &&
                other is ExpandedTraceNode &&
                traceLineIndex == other.traceLineIndex

    override fun hashCode() = 31 * super.hashCode() + traceLineIndex
}

class ExpandableTraceNode(
    project: Project,
    value: TraceTraceBoxEvent,
) : BaseTraceNode(project, value) {

    override val managedLine get() = value.firstLine

    override fun getChildren(): MutableCollection<ExpandedTraceNode> =
        List(value.otherLines.size) { index -> ExpandedTraceNode(project!!, value, index) }
            .toMutableList()

    override fun update(presentation: PresentationData) = with(presentation) {
        addText("[${value.time}] ", SimpleTextAttributes.GRAY_ATTRIBUTES)
        addText(value.firstLine.exception, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        value.firstLine.exceptionText?.let {
            addText(": ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            addText(it, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        } ?: Unit
    }

}
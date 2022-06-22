package com.tsarev.stacktracebox.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.tsarev.stacktracebox.*


/**
 * Base tree node, that contains [TraceTraceBoxEvent] event.
 */
sealed class BaseTraceNode(
    project: Project,
    value: TraceTraceBoxEvent
) : AbstractTreeNode<TraceTraceBoxEvent>(project, value) {

    open val managedLine: TraceLine? get() = null

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return mutableListOf<WholeTraceNode>()
    }
}

/**
 * Dummy event to create nodes without event.
 */
val dummy = TraceTraceBoxEvent(
    FirstTraceLine.parse("Exception"),
    emptyList(),
    "dummy",
    0,
    emptyMap()
)

/**
 * Dummy root trace node.
 */
class RootTraceNode(project: Project) : BaseTraceNode(project, dummy) {
    override fun update(presentation: PresentationData) {
        // no-op
    }
}

/**
 * Dummy root trace node.
 */
class GroupByNode(
    project: Project,
    val title: String,
    val myChildren: MutableCollection<BaseTraceNode>
) : BaseTraceNode(project, dummy) {
    override fun getChildren() = myChildren
    override fun update(presentation: PresentationData) {
        presentation.addText(title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

    override fun equals(other: Any?) = this === other
    override fun hashCode() = title.hashCode()
}

/**
 * Node, that represent single trace element.
 * It can be "Caused by" element, or just trace line.
 */
class TraceLineNode(
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
                other is TraceLineNode &&
                traceLineIndex == other.traceLineIndex

    override fun hashCode() = 31 * super.hashCode() + traceLineIndex
}

/**
 * Tree node that represents whole stack trace.
 */
class WholeTraceNode(
    project: Project,
    value: TraceTraceBoxEvent,
) : BaseTraceNode(project, value) {

    override val managedLine get() = value.firstLine

    override fun getChildren(): MutableCollection<TraceLineNode> =
        List(value.otherLines.size) { index -> TraceLineNode(project!!, value, index) }
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
package com.tsarev.stacktracebox.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.tsarev.stacktracebox.GroupByCriteria
import com.tsarev.stacktracebox.MyIcons
import com.tsarev.stacktracebox.VisibleTraceEvent
import javax.swing.Icon

class TraceGroupAction(
    text: String,
    desc: String,
    icon: Icon,
    val criteria: GroupByCriteria,
    val onAction: () -> Unit,
    var state: Boolean = false,
) : ToggleAction(text, desc, icon) {
    override fun isSelected(e: AnActionEvent) = state
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        this.state = state
        onAction()
    }
}

// Default grouping.

object GroupByFirstLine : GroupByCriteria {
    override val priority: Int = 1
    override val actionText = "Group By First Line"
    override val actionDesc = "Perform grouping by whole first stack trace line"
    override val actionIcon = MyIcons.GroupByFirstLine
    override fun group(traces: Collection<VisibleTraceEvent>) =
        traces.groupBy { it.firstLine.text }
}

object GroupByExceptionClass : GroupByCriteria {
    override val priority: Int = 0
    override val actionText = "Group By Exception Class"
    override val actionDesc = "Perform grouping by exception class name"
    override val actionIcon = AllIcons.Actions.GroupByClass
    override fun group(traces: Collection<VisibleTraceEvent>) =
        traces.groupBy { it.firstLine.exception }
}
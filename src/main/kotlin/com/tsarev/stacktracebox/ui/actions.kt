package com.tsarev.stacktracebox

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.tsarev.stacktracebox.ui.TraceBoxToolWindowFactory

/**
 * Clears all registered traces and invalidates tool window panel.
 */
@Suppress("ComponentNotRegistered")
object ClearTracesAction : AnAction(
    "Clear Traces",
    "Clears all captured traces",
    AllIcons.Actions.GC
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<TraceBoxStateManager>().traceEventsQueue.clear()
        TraceBoxToolWindowFactory.reloadAll(project)
    }
}

class ExpandAll(
        private val tree: Tree
) : DumbAwareAction(
        "Expand All",
        "Expand all",
        AllIcons.Actions.Expandall
) {
    override fun actionPerformed(e: AnActionEvent) {
        TreeUtil.expandAll(tree)
    }
}

class CollapseAll(
        private val tree: Tree
) : DumbAwareAction(
        "Collapse All",
        "Collapse all",
        AllIcons.Actions.Collapseall
) {
    override fun actionPerformed(e: AnActionEvent) {
        TreeUtil.collapseAll(tree, -1)
    }
}
package com.tsarev.stacktracebox.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil

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
package com.tsarev.stacktracebox.ui

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.DefaultMutableTreeNode

val Tree.selectedNode: BaseTraceNode?
    get() = selectionPath
        ?.lastPathComponent
        ?.tryCast<DefaultMutableTreeNode>()
        ?.userObject
        ?.tryCast<NodeDescriptor<*>>()
        ?.element
        ?.tryCast<BaseTraceNode>()

private inline fun <reified T> Any?.tryCast() = this as? T
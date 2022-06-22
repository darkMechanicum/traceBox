package com.tsarev.stacktracebox.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.treeStructure.Tree
import com.intellij.unscramble.AnalyzeStacktraceUtil
import java.awt.datatransfer.StringSelection

class CopyTraceToClipboardAction(
    private val myTree: Tree
) : AnAction(
    "Copy",
    "Copies current trace to clipboard",
    AllIcons.Actions.Copy
) {
    override fun actionPerformed(e: AnActionEvent) {
        val text = myTree.selectedNode?.value?.text
        if (text != null) {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
    }
}

class AnalyzeTraceAction(
    private val myTree: Tree
) : AnAction(
    "Analyze",
    "Open analyze trace window",
    AllIcons.Actions.ChangeView
) {
    override fun actionPerformed(e: AnActionEvent) {
        val text = myTree.selectedNode?.value?.text
        if (text != null) {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            AnalyzeStacktraceUtil.addConsole(
                e.project,
                null,
                IdeBundle.message("tab.title.stacktrace"),
                text
            )
        }
    }
}
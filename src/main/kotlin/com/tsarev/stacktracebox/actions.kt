package com.tsarev.stacktracebox

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
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
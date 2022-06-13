package com.tsarev.stacktracebox

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.impl.ContentImpl
import com.tsarev.stacktracebox.ui.TraceBoxPanel


@Service
class TraceBoxToolWindowFactory : ToolWindowFactory, DumbAware {
    companion object {

        private const val toolWindowId = "com.tsarev.tracebox"

        fun reloadAll(project: Project) {
            val affectedPanels = ToolWindowManager.getInstance(project)
                .getToolWindow(toolWindowId)
                ?.contentManager
                ?.contents
                ?.map { it.component }
                ?.filterIsInstance<TraceBoxPanel>()

            ApplicationManager.getApplication().invokeLater {
                affectedPanels?.forEach { it.reloadTraces() }
            }
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TraceBoxPanel(project)
        val content = ContentImpl(panel, "Traces", true)
        toolWindow.contentManager.addContent(content)
    }
}
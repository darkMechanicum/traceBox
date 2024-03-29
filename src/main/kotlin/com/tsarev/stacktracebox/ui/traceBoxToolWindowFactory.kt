package com.tsarev.stacktracebox.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.concurrency.AppExecutorUtil


/**
 * [TraceBoxPanel] creation and initialization factory.
 */
@Service
class TraceBoxToolWindowFactory : ToolWindowFactory {
    companion object {

        private const val toolWindowId = "com.tsarev.tracebox"

        private const val toolWindowTitle = "Tracebox"

        fun reloadAll(project: Project) {
            val affectedPanels = ToolWindowManager.getInstance(project)
                    .getToolWindow(toolWindowId)
                    ?.contentManager
                    ?.contents
                    ?.map { it.component }
                    ?.filterIsInstance<TraceBoxPanel>()

            ReadAction
                    .nonBlocking { affectedPanels?.forEach { it.reloadTraces() } }
                    .inSmartMode(project)
                    .submit(AppExecutorUtil.getAppExecutorService())
        }
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.title = toolWindowTitle
        toolWindow.stripeTitle = toolWindowTitle
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TraceBoxPanel(project)
        val content = ContentImpl(panel, "Traces", true)
        toolWindow.contentManager.addContent(content)
    }
}
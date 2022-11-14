package com.tsarev.stacktracebox.defaultExtensions

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.vcsUtil.VcsUtil
import com.tsarev.stacktracebox.AddOther
import com.tsarev.stacktracebox.GroupByCriteria
import com.tsarev.stacktracebox.VisibleTraceEvent


const val gitRevisionProp = "com.tsarev.stacktracebox.gitRevision"

@Service
class AddGitInfo(
        project: Project
) : AddOther {

    override val priority = 1

    private val vcsManager = ProjectLevelVcsManager.getInstance(project)

    private val firstGitVcsRoot = vcsManager
            .allVcsRoots
            .firstOrNull { it.vcs?.name == "Git" }

    override fun other(trace: VisibleTraceEvent): Map<String, String>? {
        val gitVcsRoot_ = firstGitVcsRoot
        if (gitVcsRoot_ != null) {
            val rootPath = VcsUtil.getFilePath(gitVcsRoot_.path)
            val historySession = gitVcsRoot_.vcs!!.vcsHistoryProvider?.createSessionFor(rootPath)
            return mapOf(
                    gitRevisionProp to (historySession?.currentRevisionNumber?.asString() ?: "")
            )
        } else return null
    }

    override fun addTextPart(trace: VisibleTraceEvent): List<Pair<String, SimpleTextAttributes>>? {
        val gitRevision = trace.other[gitRevisionProp]
        return if (gitRevision != null) {
            listOf(
                    "git: " to SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES,
                    gitRevision to SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            )
        } else null
    }
}

class GroupByGitInfo : GroupByCriteria {
    override val priority = 2
    override val actionText = "Group by git revision"
    override val actionDesc = "Perform grouping based on git revision"
    override val actionIcon = AllIcons.Vcs.Branch
    override fun group(traces: Collection<VisibleTraceEvent>) =
            traces.groupBy { it.other[gitRevisionProp] ?: "no revision" }

}
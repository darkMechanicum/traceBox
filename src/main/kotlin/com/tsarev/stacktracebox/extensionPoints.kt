package com.tsarev.stacktracebox

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.SimpleTextAttributes
import com.tsarev.stacktracebox.ui.TraceGroupAction
import javax.swing.Icon

val GROUP_BY_CRITERIA_EP = ExtensionPointName.create<GroupByCriteria>("com.tsarev.stacktracebox.groupbycriteria")

interface GroupByCriteria {
    val priority: Int
    val actionText: String
    val actionDesc: String
    val actionIcon: Icon
    fun group(traces: Collection<VisibleTraceEvent>): Map<String, Collection<VisibleTraceEvent>>
    fun createToggleAction(onAction: () -> Unit) = TraceGroupAction(
            actionText, actionDesc, actionIcon, this, onAction
    )
}

val ADD_OTHER_EP = ExtensionPointName.create<AddOther>("com.tsarev.stacktracebox.addother")

interface AddOther {
    val priority: Int
    fun other(trace: VisibleTraceEvent): Map<String, String>?
    fun addTextPart(trace: VisibleTraceEvent): List<Pair<String, SimpleTextAttributes>>?
}
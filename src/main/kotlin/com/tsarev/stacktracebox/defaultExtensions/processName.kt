package com.tsarev.stacktracebox.defaultExtensions

import com.intellij.openapi.components.Service
import com.intellij.ui.SimpleTextAttributes
import com.tsarev.stacktracebox.AddOther
import com.tsarev.stacktracebox.VisibleTraceEvent
import com.tsarev.stacktracebox.runDescriptorNameProp


@Service
class AddProcessName : AddOther {

    override val priority = 0

    override fun addTextPart(trace: VisibleTraceEvent): List<Pair<String, SimpleTextAttributes>>? {
        val processName = trace.other[runDescriptorNameProp]
        return if (processName != null) {
            listOf(
                    "process: " to SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES,
                    processName to SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            )
        } else null
    }
}
package com.tsarev.stacktracebox

import com.intellij.openapi.extensions.ExtensionPointName
import com.tsarev.stacktracebox.ui.GroupByCriteria

val GROUP_BY_CRITERIA_EP = ExtensionPointName.create<GroupByCriteria>("com.tsarev.stackTraceBox.GroupByCriteria")
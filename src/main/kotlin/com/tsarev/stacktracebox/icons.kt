package com.tsarev.stacktracebox

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon


interface MyIcons {
    companion object {
        val GroupByFirstLine: Icon = IconLoader.getIcon("/icons/GroupByFirstTraceLine.png", MyIcons::class.java)
    }
}
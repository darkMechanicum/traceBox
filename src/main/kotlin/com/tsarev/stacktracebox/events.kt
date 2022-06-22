package com.tsarev.stacktracebox

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.createSmartPointer


sealed class TraceBoxEvent

/**
 * Some text that is captured from process listener.
 */
data class TextTraceBoxEvent(
    val text: String,
    val type: String,
) : TraceBoxEvent()

/**
 * Captured trace event.
 */
data class TraceTraceBoxEvent(
    val firstLine: FirstTraceLine,
    val otherLines: List<TraceLine>,
    val type: String,
    val time: Long,
    val other: Map<String, String>,
) : TraceBoxEvent() {
    val text by lazy { "${firstLine.text}\n${otherLines.joinToString(separator = "\n") { it.text }}" }
    val allLines get() = otherLines + firstLine
}

/**
 * Process listener start event.
 */
object ProcessStartTraceBoxEvent : TraceBoxEvent()

/**
 * Process listener end event.
 */
object ProcessEndTraceBoxEvent : TraceBoxEvent()

sealed class TraceLine(
    val text: String,
) : NavigationDataProvider, NavigationAware {

    val navigationData: NavigationLineCache = NavigationLineCache(this)
    override val element get() = navigationData.element
    override val navigatable get() = navigationData.navigatable

    companion object {
        fun parseOrNull(text: String) =
            FirstTraceLine.parseOrNull(text)
                ?: CausedByTraceLine.parseOrNull(text)
                ?: AtTraceLine.parseOrNull(text)

        fun parseNonFirstLineOrNull(text: String) =
            CausedByTraceLine.parseOrNull(text)
                ?: AtTraceLine.parseOrNull(text)

        fun parse(text: String) = parseOrNull(text) ?: error("Trace line must match one of trace patterns: $text")
    }
}

class FirstTraceLine private constructor(
    text: String,
    match: MatchResult
) : TraceLine(text) {
    companion object {
        private val regex = "\\s*([^:]*(Exception|Throwable)[^:]*)(:.*)?".toRegex()
        fun parseOrNull(text: String) = regex.find(text.trim())?.let { FirstTraceLine(text, it) }
        fun parse(text: String) = parseOrNull(text) ?: error("Trace line must match first trace pattern: $text")
    }

    val exception: String

    val exceptionText: String?

    init {
        exception = match.groupValues[1]
        exceptionText =
            (if (match.groupValues.size >= 4) match.groupValues[3].removePrefix(":").trim() else null)
                ?.takeIf { it.isNotBlank() }
    }

    override fun getSmartPsiElementPointer(project: Project) =
        project.tryFindPsiFileFor(exception)?.createSmartPointer(project)

    override fun getNavigatable(project: Project, psiElement: PsiElement) = PsiNavigationSupport
        .getInstance()
        .createNavigatable(project, psiElement.containingFile.virtualFile, psiElement.textOffset)
}

class CausedByTraceLine private constructor(
    text: String,
    match: MatchResult
) : TraceLine(text) {
    companion object {
        private val regex = "\\s*Caused\\sby:\\s([^:]+)(:.+)?".toRegex()
        fun parseOrNull(text: String) = regex.find(text.trim())?.let { CausedByTraceLine(text, it) }
    }

    val causeException: String

    val causeText: String?

    init {
        causeException = match.groupValues[1]
        causeText = if (match.groupValues.size > 2) match.groupValues[2].removePrefix(":") else null
    }

    override fun getSmartPsiElementPointer(project: Project) = project
        .tryFindPsiFileFor(causeException)
        ?.createSmartPointer(project)

    override fun getNavigatable(project: Project, psiElement: PsiElement) = PsiNavigationSupport
        .getInstance()
        .createNavigatable(project, psiElement.containingFile.virtualFile, psiElement.textOffset)
}

class AtTraceLine private constructor(
    text: String,
    match: MatchResult
) : TraceLine(text) {
    companion object {
        private val regex = "[\\t\\s]*at\\s([^(]+)(\\(([^)]+)(:\\d+)\\)?)?".toRegex()
        fun parseOrNull(text: String) = regex.find(text.trim())?.let { AtTraceLine(text, it) }
    }

    val methodName: String

    val classSimpleName: String?

    val position: Int?

    init {
        methodName = match.groupValues[1]
        classSimpleName = if (match.groupValues.size >= 4) match.groupValues[3] else null
        position = if (match.groupValues.size >= 5) match.groupValues[4].removePrefix(":").toIntOrNull() else null
    }

    private val fqn by lazy {
        val indexOfInner = methodName.indexOf('$')
        val fqnEndIndex = if (indexOfInner != -1) indexOfInner - 1
        else methodName.lastIndexOf('.') - 1
        methodName.substring(0..fqnEndIndex)
    }

    override fun getSmartPsiElementPointer(project: Project) = project
        .tryFindPsiFileFor(fqn)
        ?.createSmartPointer(project)

    override fun getNavigatable(project: Project, psiElement: PsiElement) = PsiNavigationSupport
        .getInstance()
        .createNavigatable(project, psiElement.containingFile.virtualFile, getOffset(project, psiElement))

    private fun getOffset(project: Project, psiElement: PsiElement) = position?.let {
        PsiDocumentManager.getInstance(project)
            .getDocument(psiElement.containingFile)
            ?.getLineStartOffset(it - 1)
    } ?: 0
}

fun Project.tryFindPsiFileFor(fqn: String): PsiClass? {
    val psiManager = PsiManager.getInstance(this)
    return ClassUtil.findPsiClass(psiManager, fqn)
}
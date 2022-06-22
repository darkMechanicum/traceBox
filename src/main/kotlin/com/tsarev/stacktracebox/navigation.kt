package com.tsarev.stacktracebox

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow


@Service
class NavigationCalculationService(
    private val project: Project
) : Disposable, DumbModeListener, DumbAware {

    private val myScope = CoroutineScope(Job())

    private val linesEventsWarmupReplay = 1000

    private val needToCalculateNavigation =
        MutableSharedFlow<NavigationLineCache>(replay = linesEventsWarmupReplay)

    private val myRecalculatedFlow = MutableSharedFlow<Unit>()

    val recalculatedFlow: Flow<Unit> get() = myRecalculatedFlow

    private val filteredTraces = project.service<FilteredTraceEvents>().traceFlow

    private val application = ApplicationManager.getApplication()

    @Volatile
    private var indexAvailableDeferred: CompletableDeferred<Unit>? = CompletableDeferred()

    override fun enteredDumbMode() {
        indexAvailableDeferred = CompletableDeferred()
    }

    override fun exitDumbMode() {
        runBlocking {
            indexAvailableDeferred?.complete(Unit)
            indexAvailableDeferred = null
        }
    }

    fun <T : TraceBoxEvent> scheduleCalculateNavigation(value: T) {
        runBlocking {
            if (value is TraceTraceBoxEvent)
                value.allLines.forEach {
                    it.navigationData.scheduleCalculateNavigation()
                }
        }
    }

    suspend fun NavigationLineCache.scheduleCalculateNavigation() {
        if (!isScheduledForRecalculation) {
            isScheduledForRecalculation = true
            needToCalculateNavigation.emit(this)
        }
    }

    init {
        myScope.launch {
            filteredTraces.collect { scheduleCalculateNavigation(it) }
        }

        myScope.launch {
            needToCalculateNavigation.collect {
                try {
                    indexAvailableDeferred?.await()
                    application.runReadAction {
                        it.recalculate(project)
                        runBlocking {
                            myRecalculatedFlow.emit(Unit)
                        }
                    }
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                }
            }
        }

        project.messageBus.connect(this).subscribe(DumbService.DUMB_MODE, this)
    }

    override fun dispose() = myScope.cancel()
}

/**
 * Interface to get navigation data for element.
 */
interface NavigationAware {
    val element: PsiElement?
    val navigatable: Navigatable?
}

/**
 * Interface to calculate navigation data for element.
 */
interface NavigationDataProvider {

    fun getSmartPsiElementPointer(
        project: Project
    ): SmartPsiElementPointer<out PsiElement>?

    fun getNavigatable(
        project: Project,
        psiElement: PsiElement
    ): Navigatable?
}

// TODO Add listening for PSI Change.
/**
 * Grouped navigation cache logic.
 */
class NavigationLineCache(
    private val usedProvider: NavigationDataProvider,
) : NavigationAware {

    @Volatile
    var cachedSmartPointer: SmartPsiElementPointer<out PsiElement>? = null

    @Volatile
    var cachedNavigatable: CachedNavigatable? = null

    @Volatile
    internal var isScheduledForRecalculation = false

    override val element: PsiElement? get() = cachedSmartPointer?.element

    override val navigatable: Navigatable? get() = cachedNavigatable?.navigatable

    private fun getSmartPsiElementPointerCached(project: Project) =
        cachedSmartPointer ?: usedProvider.getSmartPsiElementPointer(project)?.also { cachedSmartPointer = it }

    data class CachedNavigatable(val usedPsiElement: PsiElement, val navigatable: Navigatable)

    fun recalculate(project: Project) {
        isScheduledForRecalculation = false
        val usedNavigatable = cachedNavigatable
        val psiElement = getSmartPsiElementPointerCached(project)?.element
        if (psiElement != null && (usedNavigatable == null || usedNavigatable.usedPsiElement != psiElement)) {
            usedProvider.getNavigatable(project, psiElement)?.also {
                cachedNavigatable = CachedNavigatable(psiElement, it)
            }
        }
    }

}
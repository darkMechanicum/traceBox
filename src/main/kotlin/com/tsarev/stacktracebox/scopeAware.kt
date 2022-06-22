package com.tsarev.stacktracebox

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Just a service base class, that has scope and cancels it on dispose.
 */
abstract class ServiceWithScope : ScopeAwareDisposable {

    override val myScope = CoroutineScope(EmptyCoroutineContext)

}

/**
 * Interface that adds scope cancellation on disposal.
 */
interface ScopeAwareDisposable : Disposable {

    val myScope: CoroutineScope

    override fun dispose() {
        myScope.cancel()
    }
}
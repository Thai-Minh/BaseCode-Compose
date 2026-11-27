package com.looper.base.repository

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for repository calls: failures become a failed [Result], but coroutine
 * cancellation is rethrown so the caller's scope still gets cancelled.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }

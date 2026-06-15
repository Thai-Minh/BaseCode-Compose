package com.looper.base.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

object TransformationsUnit {

    /**
     * Map 1 nguồn dữ liệu LiveData
     */
    fun <X, Z> map(
        scope: CoroutineScope,
        sourceX: LiveData<X>,
        mapFunction: suspend (x: X?) -> Z
    ): LiveData<Z> {
        val result = MediatorLiveData<Z>()
        var debounceJob: Job? = null

        result.addSource(sourceX) { x ->
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(x)
            }
        }
        return result
    }

    /**
     * Map 2 nguồn dữ liệu LiveData
     */
    fun <X, Y, T> map(
        scope: CoroutineScope,
        sourceX: LiveData<X>,
        sourceY: LiveData<Y>,
        mapFunction: suspend (x: X?, y: Y?) -> T
    ): LiveData<T> {
        val result = MediatorLiveData<T>()
        var debounceJob: Job? = null

        val update = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(sourceX.value, sourceY.value)
            }
        }

        result.addSource(sourceX) { update() }
        result.addSource(sourceY) { update() }
        return result
    }

    /**
     * Map 3 nguồn dữ liệu LiveData
     */
    fun <X, Y, Z, T> map(
        scope: CoroutineScope,
        sourceX: LiveData<X>,
        sourceY: LiveData<Y>,
        sourceZ: LiveData<Z>,
        mapFunction: suspend (x: X?, y: Y?, z: Z?) -> T
    ): LiveData<T> {
        val result = MediatorLiveData<T>()
        var debounceJob: Job? = null

        val update = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(sourceX.value, sourceY.value, sourceZ.value)
            }
        }

        result.addSource(sourceX) { update() }
        result.addSource(sourceY) { update() }
        result.addSource(sourceZ) { update() }
        return result
    }

    /**
     * Map 4 nguồn dữ liệu LiveData
     */
    fun <A, B, C, D, T> map(
        scope: CoroutineScope,
        sourceA: LiveData<A>,
        sourceB: LiveData<B>,
        sourceC: LiveData<C>,
        sourceD: LiveData<D>,
        mapFunction: suspend (a: A?, b: B?, c: C?, d: D?) -> T
    ): LiveData<T> {
        val result = MediatorLiveData<T>()
        var debounceJob: Job? = null

        val update = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(sourceA.value, sourceB.value, sourceC.value, sourceD.value)
            }
        }

        result.addSource(sourceA) { update() }
        result.addSource(sourceB) { update() }
        result.addSource(sourceC) { update() }
        result.addSource(sourceD) { update() }
        return result
    }

    /**
     * Map 5 nguồn dữ liệu
     */
    fun <A, B, C, D, E, T> map(
        scope: CoroutineScope,
        sourceA: LiveData<A>,
        sourceB: LiveData<B>,
        sourceC: LiveData<C>,
        sourceD: LiveData<D>,
        sourceE: LiveData<E>,
        mapFunction: suspend (a: A?, b: B?, c: C?, d: D?, e: E?) -> T
    ): LiveData<T> {
        val result = MediatorLiveData<T>()
        var debounceJob: Job? = null

        val update = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(
                    sourceA.value, sourceB.value, sourceC.value, sourceD.value, sourceE.value
                )
            }
        }

        result.addSource(sourceA) { update() }
        result.addSource(sourceB) { update() }
        result.addSource(sourceC) { update() }
        result.addSource(sourceD) { update() }
        result.addSource(sourceE) { update() }
        return result
    }

    /**
     * Map 6 nguồn dữ liệu
     */
    fun <A, B, C, D, E, F, T> map(
        scope: CoroutineScope,
        sourceA: LiveData<A>,
        sourceB: LiveData<B>,
        sourceC: LiveData<C>,
        sourceD: LiveData<D>,
        sourceE: LiveData<E>,
        sourceF: LiveData<F>,
        mapFunction: suspend (a: A?, b: B?, c: C?, d: D?, e: E?, f: F?) -> T
    ): LiveData<T> {
        val result = MediatorLiveData<T>()
        var debounceJob: Job? = null

        val update = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(
                    sourceA.value, sourceB.value, sourceC.value, sourceD.value, sourceE.value, sourceF.value
                )
            }
        }

        result.addSource(sourceA) { update() }
        result.addSource(sourceB) { update() }
        result.addSource(sourceC) { update() }
        result.addSource(sourceD) { update() }
        result.addSource(sourceE) { update() }
        result.addSource(sourceF) { update() }
        return result
    }

    /**
     * Map 7 nguồn dữ liệu
     */
    fun <A, B, C, D, E, F, G, T> map(
        scope: CoroutineScope,
        sourceA: LiveData<A>,
        sourceB: LiveData<B>,
        sourceC: LiveData<C>,
        sourceD: LiveData<D>,
        sourceE: LiveData<E>,
        sourceF: LiveData<F>,
        sourceG: LiveData<G>,
        mapFunction: suspend (a: A?, b: B?, c: C?, d: D?, e: E?, f: F?, g: G?) -> T
    ): LiveData<T> {
        val result = MediatorLiveData<T>()
        var debounceJob: Job? = null

        val update = {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(50.milliseconds)
                result.value = mapFunction(
                    sourceA.value, sourceB.value, sourceC.value, sourceD.value,
                    sourceE.value, sourceF.value, sourceG.value
                )
            }
        }

        result.addSource(sourceA) { update() }
        result.addSource(sourceB) { update() }
        result.addSource(sourceC) { update() }
        result.addSource(sourceD) { update() }
        result.addSource(sourceE) { update() }
        result.addSource(sourceF) { update() }
        result.addSource(sourceG) { update() }
        return result
    }
}
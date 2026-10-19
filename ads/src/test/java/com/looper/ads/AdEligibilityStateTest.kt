package com.looper.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdEligibilityStateTest {
    @Test
    fun `default is disabled before binding`() {
        assertFalse(AdEligibilityState().isEligible.value)
    }

    @Test
    fun `only explicitly allowed and nonpremium is eligible`() {
        val inputs = listOf(null, false, true)
        for (remote in inputs) {
            for (premium in inputs) {
                withEligibility(remote, premium) { state, _, _ ->
                    assertEquals(remote == true && premium == false, state.isEligible.value)
                }
            }
        }
    }

    @Test
    fun `premium and remote vetoes take priority through transitions`() {
        withEligibility(null, null) { state, remote, premium ->
            remote.value = true
            assertFalse(state.isEligible.value)
            premium.value = false
            assertTrue(state.isEligible.value)
            premium.value = true
            assertFalse(state.isEligible.value)
            remote.value = false
            remote.value = true
            assertFalse(state.isEligible.value)
            premium.value = false
            assertTrue(state.isEligible.value)
            remote.value = false
            assertFalse(state.isEligible.value)
            remote.value = true
            assertTrue(state.isEligible.value)
            premium.value = null
            assertFalse(state.isEligible.value)
            premium.value = false
            assertTrue(state.isEligible.value)
            remote.value = null
            assertFalse(state.isEligible.value)
        }
    }

    @Test
    fun `repeated binding cannot install a second writer or reset eligibility`() {
        withEligibility(true, true) { state, _, premium ->
            val otherScope = CoroutineScope(Job() + Dispatchers.Unconfined)
            try {
                state.bind(otherScope, MutableStateFlow(true), MutableStateFlow(false))
                assertFalse(state.isEligible.value)
                premium.value = false
                assertTrue(state.isEligible.value)
            } finally {
                otherScope.cancel()
            }
        }
    }

    @Test
    fun `initialization API cannot accept an eligibility override`() {
        val initializer = Class.forName("com.looper.ads.AdsHelperKt")
            .declaredMethods.single { it.name == "initAd" }
        assertFalse(initializer.parameterTypes.contains(Boolean::class.javaPrimitiveType))
        assertFalse(initializer.declaringClass.declaredMethods.any { it.name == "setSupportAd" })
    }

    @Test
    fun `no billing emission keeps eligibility disabled`() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val state = AdEligibilityState()
        val premium = MutableSharedFlow<Boolean?>()
        try {
            state.bind(scope, MutableStateFlow(true), premium)
            assertFalse(state.isEligible.value)
            val eligible = launch(Dispatchers.Unconfined) {
                state.isEligible.first { it }
            }
            premium.emit(false)
            eligible.join()
            assertTrue(state.isEligible.value)
        } finally {
            scope.cancel()
        }
    }

    private fun withEligibility(
        remote: Boolean?,
        premium: Boolean?,
        assertions: (AdEligibilityState, MutableStateFlow<Boolean?>, MutableStateFlow<Boolean?>) -> Unit,
    ) {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val remoteState = MutableStateFlow(remote)
        val premiumState = MutableStateFlow(premium)
        val state = AdEligibilityState()
        try {
            state.bind(scope, remoteState, premiumState)
            assertions(state, remoteState, premiumState)
        } finally {
            scope.cancel()
        }
    }
}

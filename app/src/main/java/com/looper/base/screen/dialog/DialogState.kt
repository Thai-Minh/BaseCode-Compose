package com.looper.base.screen.dialog

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel

val LocalDialogState = compositionLocalOf {
    DialogState()
}

@Stable
class DialogState {
    private val noInternetDialogChannel = Channel<Boolean>()
    var showNoInternetDialog by mutableStateOf(false)
        private set

    suspend fun showNoInternetDialog(): Boolean? {
        showNoInternetDialog = true

        return try {
            noInternetDialogChannel.receive()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun dismissNoInternetDialog(result: Boolean) {
        showNoInternetDialog = false

        noInternetDialogChannel.trySend(result)
    }
}
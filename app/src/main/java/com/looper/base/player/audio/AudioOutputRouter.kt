package com.looper.base.player.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AudioOutputType { SPEAKER, WIRED, BLUETOOTH, USB, HEARING_AID, OTHER }

data class AudioOutput(
    val id: Int,
    val name: String,
    val type: AudioOutputType,
)

class AudioOutputRouter(
    context: Context,
) {

    private companion object {
        val USABLE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)

    val outputs: StateFlow<List<AudioOutput>>
        field = MutableStateFlow(value = emptyList())

    val selected: StateFlow<AudioOutput?>
        field = MutableStateFlow(value = null)

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun select(outputId: Int?) {
        selected.value = outputId?.let { id ->
            outputs.value.firstOrNull {
                it.id == id
            }
        }
    }

    fun deviceInfo(output: AudioOutput?): AudioDeviceInfo? =
        output?.let { target ->
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.id == target.id
            }
        }

    private fun refresh() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter {
                it.type in USABLE_TYPES
            }
            .map {
                AudioOutput(
                    id = it.id,
                    name = it.productName?.toString()?.takeIf { name -> name.isNotBlank() }
                        ?: it.type.toString(),
                    type = outputType(type = it.type),
                )
            }
        outputs.value = devices
        if (selected.value?.let { current ->
                devices.none {
                    it.id == current.id
                }
            } == true) {
            selected.value = null
        }
    }

    private fun outputType(type: Int): AudioOutputType = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputType.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioOutputType.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioOutputType.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputType.USB
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputType.HEARING_AID
        else -> AudioOutputType.OTHER
    }
}

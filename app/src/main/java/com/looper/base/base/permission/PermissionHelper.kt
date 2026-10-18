package com.looper.base.base.permission

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import com.example.permissions.helper.SpecialPermission
import com.example.permissions.helper.ktx.hasPermissions
import com.example.permissions.helper.ktx.requestPermission
import com.looper.ads.AdsManager
import com.looper.base.R
import com.looper.base.base.ui.stringOf
import com.looper.base.screen.dialog.DialogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.collections.isNotEmpty
import kotlin.coroutines.resume


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val STORAGE_PERMISSION_ANDROID_13 =
    listOf(
//        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO
    )

private val STORAGE_PERMISSION_ANDROID =
    listOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

val STORE_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    STORAGE_PERMISSION_ANDROID_13
else
    STORAGE_PERMISSION_ANDROID

fun safePermission(
    activity: ComponentActivity,
    scope: CoroutineScope,
    dialogState: DialogState,
    permissions: List<String> = emptyList(),
    specialPermission: List<SpecialPermission> = emptyList(),
    onGranted: () -> Unit,
    onDenied: () -> Unit = {}
) {
    if (activity.hasPermissions(permissions) && activity.hasPermissions(specialPermission)) {
        onGranted()
        return
    }

    val title = "stringOf(R.string.permission_required)" // TODO

    AdsManager.ignoreShowAppOpenAdOnetime()

    scope.launch {
        if (permissions.isNotEmpty()) {
            activity.requestPermission(
                permissions = permissions,
                onDenied = {
                    onDenied()
                },
                onGranted = {
                    if (specialPermission.isNotEmpty()) {
                        scope.launch {
                            activity.requestPermission(
                                scope = scope,
                                dialogState = dialogState,
                                title = title,
                                specialPermission = specialPermission,
                                onGranted = onGranted,
                                onDenied = onDenied
                            )
                        }
                    } else {
                        onGranted()
                    }
                }
            )
        } else if (specialPermission.isNotEmpty()) {
            activity.requestPermission(
                scope = scope,
                dialogState = dialogState,
                title = title,
                specialPermission = specialPermission,
                onGranted = onGranted,
                onDenied = onDenied
            )
        } else {
            onGranted()
        }
    }
}

private suspend fun ComponentActivity.requestPermission(
    scope: CoroutineScope,
    dialogState: DialogState,
    title: String,
    specialPermission: List<SpecialPermission> = emptyList(),
    onDenied: () -> Unit = {},
    onGranted: () -> Unit
) {

    if (specialPermission.isEmpty()) {
        onGranted()
        return
    }

    for (special in specialPermission) {
        if (!requestSpecialPermission(
                activity = this,
                scope = scope,
                dialogState = dialogState,
                title = title,
                specialPermission = special
            )
        ) {
            return
        }
    }

    onDenied()
}

private suspend fun requestSpecialPermission(
    activity: ComponentActivity,
    scope: CoroutineScope,
    dialogState: DialogState,
    title: String,
    specialPermission: SpecialPermission,
): Boolean = suspendCancellableCoroutine { continuation ->

    scope.launch {
        if (activity.hasPermissions(specialPermission))
            continuation.resume(true)
        else {
            // TODO
//            val accept = dialogState.showRequestPermissionDialog(
//                title = title,
//                description = getString(R.string.this_app_needs_permission_to_access_and_display_all_videos_stored_on_your_device),
//                iconId = R.drawable.ic_require_permission
//            )
//
//            if (accept) {
//                activity.requestPermissionWithout(
//                    specialPermission = specialPermission,
//                    onDenied = {
//                        continuation.resume(false)
//                    },
//                    onGranted = {
//                        continuation.resume(true)
//                    }
//                )
//            }
        }
    }
}

fun ComponentActivity.hasPermissions(
    specialPermission: List<SpecialPermission> = emptyList(),
): Boolean {
    for (permission in specialPermission) {
        if (!hasPermissions(permission)) return false
    }

    return true
}

fun ComponentActivity.requestPermissionWithout(
    specialPermission: SpecialPermission,
    serviceClazz: Class<*>? = null,
    onDenied: () -> Unit = {},
    onGranted: () -> Unit
) {
    requestPermission(
        permission = specialPermission,
        clazz = serviceClazz,
        onDenied = {
            onDenied()
        },
        onGranted = onGranted
    )
}
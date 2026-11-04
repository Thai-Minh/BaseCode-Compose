/**
*Created by: ThaiND
*/

package com.looper.base.iap.data

enum class IapReadiness {
    LoadingConfig,
    Connecting,
    Ready,
    Unavailable
}

enum class IapActionResult {
    Started,
    NotReady,
    Failed
}

data class IapSessionState(
    val readiness: IapReadiness = IapReadiness.LoadingConfig,
    val message: String? = null,
)

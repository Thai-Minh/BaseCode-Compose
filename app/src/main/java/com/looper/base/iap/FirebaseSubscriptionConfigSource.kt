/**
*Created by: ThaiND
*/

package com.looper.base.iap

import android.content.Context
import com.looper.base.utils.firebase.FRRemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores only validated configuration, never premium or purchase verification state. */
class FirebaseSubscriptionConfigSource(context: Context) : SubscriptionConfigSource {
    private val preferences = context.applicationContext.getSharedPreferences(
        "iap_v2_catalog",
        Context.MODE_PRIVATE,
    )

    override suspend fun readActivatedPayload(): String {
        return FRRemoteConfig.readActivatedSubscriptionPayload()
    }

    override suspend fun readLastKnownGood(): String? = withContext(context = Dispatchers.IO) {
        preferences.getString("validated_payload", null)
    }

    override suspend fun saveLastKnownGood(payload: String) {
        withContext(context = Dispatchers.IO) {
            check(value = preferences.edit().putString("validated_payload", payload).commit()) {
                "Could not persist validated IAP catalog"
            }
        }
    }
}

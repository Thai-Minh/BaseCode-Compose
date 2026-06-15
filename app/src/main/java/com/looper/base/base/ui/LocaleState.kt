package com.looper.base.base.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.MyApplication
import com.looper.base.base.storage.LanguageCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

val LocalLocaleContext = compositionLocalOf<Context?> {
    error("locale not created")
}

@Composable
fun LocaleWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val langCode by getLangCode(isPreview).collectAsStateWithLifecycle(initialValue = null)

    CompositionLocalProvider(
        LocalLocaleContext provides context.createConfig(langCode),
    ) {
        content()
    }
}

private fun getLangCode(isPreview: Boolean = false): Flow<String> { //create with build preview
    return if (isPreview) flowOf("en") else LanguageCode
}

@Composable
fun stringResourceCustom(@StringRes id: Int, vararg formatArgs: Any): String {
    val localLocaleContext = LocalLocaleContext.current
    val isPreview = LocalInspectionMode.current

    if (isPreview) {
        val resources = LocalResources.current
        return resources.getString(id, *formatArgs)
    } else {
        val string = localLocaleContext?.getString(id, *formatArgs)
        return string ?: ""
    }
}

fun Context.createConfig(langCode: String?): Context? {
    if (langCode == null) return null

    val config = Configuration(resources.configuration)
    config.setLocale(Locale(langCode))

    return createConfigurationContext(config)
}

suspend fun getString(@StringRes id: Int, vararg formatArgs: Any): String {
    val langCode = getLangCode().firstOrNull() ?: stringOf(id, formatArgs)
    return MyApplication.instance.createConfig(langCode)?.getString(id, formatArgs)
        ?: stringOf(id, formatArgs)
}

fun stringOf(@StringRes id: Int, vararg formatArgs: Any) =
    MyApplication.instance.getString(id, formatArgs)
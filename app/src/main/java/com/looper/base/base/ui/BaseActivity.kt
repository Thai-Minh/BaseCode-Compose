package com.looper.base.base.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import com.looper.base.iap.utils.finishActivityFadeOutAnimation
import java.util.Locale

abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: T

    companion object {
        const val IAP_SUCCESS = "IAP_SUCCESS"
        private const val LANGUAGE_CODE_KEY = "language_code_key"
    }

    override fun attachBaseContext(newBase: Context?) {
        val languageCode = getLanguageCodeFromIntent(intent)

        if (languageCode == null)
            super.attachBaseContext(newBase)
        else
            super.attachBaseContext(updateLocale(newBase!!, languageCode))
    }

    // --- Activity lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initBinding()

        setContentView(binding.root)

        applyEdgeToEdge()
        initSystemInsets()

        initView()
        addEvent()
        addObserver()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@BaseActivity.handleOnBackPressed()
            }
        })

        setContentView(binding.root)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onStart() {
        super.onStart()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // --- Locale attach ---


    protected open fun initBinding() {
        binding = createBinding()
    }

    open fun initView() {}

    protected open fun addEvent() {}

    protected open fun addObserver() {}

    abstract fun createBinding(): T

    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // --- Overridable hooks ---
    protected open fun isLightStatusBar(): Boolean = false
    protected open fun isLightNavigationBar(): Boolean = false

    protected open fun handleOnBackPressed() {
        finish()
    }

    protected open fun onSystemInsetsChanged(systemBars: Insets, keyboard: Insets) {
        // Default implementation does nothing
    }

    fun getLanguageCodeFromIntent(intent: Intent?): String? {
        return intent?.getStringExtra(LANGUAGE_CODE_KEY)
    }

    fun attachLanguageFromContext(context: Context, intent: Intent) {
        getCurrentLanguageCode(context)?.let { languageCode ->
            intent.putExtra(LANGUAGE_CODE_KEY, languageCode)
        }
    }

    fun finishWithIAPState(isSuccess: Boolean) {
        val data = Intent().apply {
            putExtra(IAP_SUCCESS, isSuccess)
        }
        setResult(RESULT_OK, data)
        finishActivityFadeOutAnimation()
    }

    fun wrapLanguage(context: Context, intent: Intent): Intent {
        attachLanguageFromContext(context, intent)
        return intent
    }

    fun getCurrentLanguageCode(context: Context): String? {
        val configuration: Configuration = context.resources.configuration
        val locale: Locale? = configuration.locale
        if (locale != null) {
            return locale.language
        }

        return null
    }

    fun updateLocale(context: Context, language: String): Context {
        return updateResources(context, language)
    }

    fun setAppearanceLightBars(
        activity: Activity,
        lightStatusBar: Boolean,
        lightNavigationBar: Boolean
    ) {
        val window = activity.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightStatusBar
        controller.isAppearanceLightNavigationBars = lightNavigationBar
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    // --- UI setup ---
    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setAppearanceLightBars(this, isLightStatusBar(), isLightNavigationBar())
    }

    private fun initSystemInsets() {
        val window = window ?: return
        val decorView = window.decorView

        ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())

            onSystemInsetsChanged(systemBars, keyboard)
            insets
        }
    }

    class LanguageIntent(
        context: Context,
        cls: Class<*>,
        languageCode: String? = null
    ) : Intent(context, cls) {

        init {
            val activity = context as? BaseActivity<*>

            if (languageCode != null) {
                this.putExtra(LANGUAGE_CODE_KEY, languageCode)
            } else {
                activity?.attachLanguageFromContext(context = context, intent = this)
            }
        }
    }
}
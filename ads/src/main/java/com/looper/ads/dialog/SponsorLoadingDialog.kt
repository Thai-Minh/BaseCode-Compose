package com.looper.ads.dialog

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.looper.ads.AdsDataPreference
import com.looper.ads.R
import com.looper.ads.databinding.DialogLoadingSponsorBinding
import com.looper.ads.dp2Px
import java.util.Locale

class SponsorLoadingDialog : DialogFragment() {
    private var _binding: DialogLoadingSponsorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.DialogSponsor)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        updateLanguage()
        _binding = DialogLoadingSponsorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSystemInsets()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        binding.progress.smoothToShow()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.progress.smoothToHide()
        _binding = null
    }

    fun showOnce(manager: FragmentManager, tag: String) {
        if (manager.findFragmentByTag(tag) == null) {
            show(manager, tag)
        }
    }

    private fun initSystemInsets(callback: () -> Unit = {}) {
        val view = view ?: return

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            (binding.loadingDotAnimation.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(0, 0, 0, systemBars.bottom + requireContext().dp2Px(16))
            }

            callback()

            insets
        }
    }

    private fun updateLanguage() {
        val locale = Locale(AdsDataPreference.langCodeAds)
        val config = Configuration(requireContext().resources.configuration)
        config.setLocale(locale)
        requireContext().applicationContext.resources.updateConfiguration(
            config, requireContext().resources.displayMetrics
        )
        resources.updateConfiguration(config, requireContext().resources.displayMetrics)
    }

    companion object {
        fun newInstance(): SponsorLoadingDialog {
            return SponsorLoadingDialog()
        }
    }
}
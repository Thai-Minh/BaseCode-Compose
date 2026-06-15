package com.looper.ads.dialog

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.looper.ads.AdsDataPreference
import com.looper.ads.AdsManager
import com.looper.ads.R
import com.looper.ads.databinding.DialogWelcomeBackBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class WelcomeBackDialog : DialogFragment() {
    private var _binding: DialogWelcomeBackBinding? = null
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
        _binding = DialogWelcomeBackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        delayFake()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showOnce(manager: FragmentManager, tag: String) {
        if (manager.findFragmentByTag(tag) == null) {
            show(manager, tag)
        }
    }

    private fun delayFake() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)
            AdsManager.handleTemporarilyAppOpenResumed(isEnable = true)
            delay(250)

            AdsManager.showAdOpen(requireActivity())

            if (isAdded)
                dismissAllowingStateLoss()
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
        fun newInstance(): WelcomeBackDialog {
            return WelcomeBackDialog()
        }
    }
}
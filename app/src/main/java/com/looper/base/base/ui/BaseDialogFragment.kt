package com.looper.base.base.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding> : DialogFragment() {
    private var _binding: VB? = null

    protected val binding get() = _binding!!

    open var isCancel = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = getViewBinding(inflater, container)

        configWindow()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        measureWindow()

        dialog?.let {
            it.setCanceledOnTouchOutside(isCancel)
            it.setCancelable(isCancel)
        }
    }

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    open fun configWindow() {
        val window = dialog?.window

        if (dialog != null && window != null) {
            window.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                requestFeature(Window.FEATURE_NO_TITLE)
            }
        }
    }

    open fun measureWindow() {
        val window = dialog?.window
        if (dialog != null && window != null) {
            window.apply {
                val orientation = requireContext().resources.configuration.orientation
                val widthPercent = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 0.5f
                else 0.85f

                setWidthPercent(percentage = widthPercent, window = window)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setWidthPercent(percentage: Float, window: Window) {
        val params = window.attributes
        val dm = Resources.getSystem().displayMetrics

        if (percentage > 0f) {
            val percentWidth = dm.widthPixels * percentage
            params.width = percentWidth.toInt()
        }

        params.height = ViewGroup.LayoutParams.WRAP_CONTENT

        window.attributes = params
    }

    fun showOnce(manager: FragmentManager, tag: String) {
        if (!manager.isStateSaved && manager.findFragmentByTag(tag) == null) {
            show(manager, tag)
        }
    }
}
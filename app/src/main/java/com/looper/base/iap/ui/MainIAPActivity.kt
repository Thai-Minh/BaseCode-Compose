package com.looper.base.iap.ui

import android.os.Bundle
import androidx.activity.addCallback
import com.looper.base.base.ui.BaseActivity
import com.looper.base.databinding.ActivityMainIapBinding

class MainIAPActivity : BaseActivity<ActivityMainIapBinding>()  {

    override fun createBinding(): ActivityMainIapBinding {
        return ActivityMainIapBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) {
            finishWithIAPState(isSuccess = false)
        }
    }
}
package com.zeusro.wechatmagician.frontend

import android.content.Context
import android.content.res.Configuration
import android.support.multidex.MultiDexApplication
import com.zeusro.wechatmagician.util.LocaleUtil

class WMApplication: MultiDexApplication() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtil.onAttach(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        LocaleUtil.onAttach(this)
    }
}
package com.zeusro.wechatmagician.backend.plugins

import android.widget.Button
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.Global.SETTINGS_AUTO_LOGIN
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.storage.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findFirstFieldByExactType

// 自动确认扫码操作
object AutoLogin {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val pkg = WechatPackage

    @JvmStatic fun enableAutoLogin() {
        findAndHookMethod(pkg.WebWXLoginUI, "onCreate", C.Bundle, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                if (preferences?.getBoolean(SETTINGS_AUTO_LOGIN, false) == true) {
                    val clazz = param.thisObject.javaClass
                    val field = findFirstFieldByExactType(clazz, C.Button)
                    val button = field.get(param.thisObject) as Button?
                    button?.performClick()
                }
            }
        })
    }
}
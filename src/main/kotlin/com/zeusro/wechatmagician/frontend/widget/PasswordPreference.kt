package com.zeusro.wechatmagician.frontend.widget

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.util.AttributeSet
import com.zeusro.wechatmagician.util.PasswordUtil

class PasswordPreference : EditTextPreference {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onClick() {
        val pref = preferenceManager.sharedPreferences
        val encrypted = pref.getString(key, "")
        if (encrypted == "") {
            PasswordUtil.createPassword(context, "Wechat Magician", pref, key)
        } else {
            PasswordUtil.changePassword(context, "Wechat Magician", pref, key)
        }
    }
}
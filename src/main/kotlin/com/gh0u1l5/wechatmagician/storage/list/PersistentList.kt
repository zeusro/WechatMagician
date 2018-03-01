package com.zeusro.wechatmagician.storage.list

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.zeusro.wechatmagician.Global.tryWithThread

open class PersistentList(private val preferenceName: String) : BaseList<String>() {

    @Volatile var preference: SharedPreferences? = null

    fun init(context: Context) {
        tryWithThread {
            preference = context.getSharedPreferences(preferenceName, MODE_PRIVATE)
            this += preference!!.all.filter { it.value == true }.keys
        }
    }

    override operator fun plusAssign(value: String) {
        super.plusAssign(value)
        preference?.edit()?.putBoolean(value, true)?.apply()
    }

    override operator fun minusAssign(value: String) {
        super.minusAssign(value)
        preference?.edit()?.putBoolean(value, false)?.apply()
    }
}
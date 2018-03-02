package com.zeusro.wechatmagician.backend.plugins

import android.widget.BaseAdapter
import com.zeusro.wechatmagician.Global.SETTINGS_CHATTING_CHATROOM_HIDER
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.frontend.wechat.AdapterHider
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.Preferences
import com.zeusro.wechatmagician.storage.list.ChatroomHideList
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.getObjectField

// 群聊隐藏
object ChatroomHider {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val str = LocalizedStrings
    private val pkg = WechatPackage

    fun changeChatroomStatus(username: String?, hide: Boolean) {
        if (username == null) {
            return
        }
        if (hide) {
            ChatroomHideList += username
        } else {
            ChatroomHideList -= username
        }
        pkg.ConversationAdapterObject.get()?.notifyDataSetChanged()
    }

    fun onAdapterCreated(param: XC_MethodHook.MethodHookParam) {
        if (!preferences!!.getBoolean(SETTINGS_CHATTING_CHATROOM_HIDER, false)) {
            return
        }
        val adapter = param.thisObject as BaseAdapter
        AdapterHider.register(adapter, "Chatroom Hider", { item ->
            val username = getObjectField(item, "field_username")
            username in ChatroomHideList
        })
    }
}
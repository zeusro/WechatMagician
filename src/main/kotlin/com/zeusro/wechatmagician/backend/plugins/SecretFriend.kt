package com.zeusro.wechatmagician.backend.plugins

import android.app.Activity
import android.content.Context
import android.widget.BaseAdapter
import android.widget.Toast
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.Global.SETTINGS_SECRET_FRIEND
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.frontend.wechat.AdapterHider
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.LocalizedStrings.PROMPT_USER_NOT_FOUND
import com.zeusro.wechatmagician.storage.Preferences
import com.zeusro.wechatmagician.storage.database.MainDatabase.getContactByNickname
import com.zeusro.wechatmagician.storage.list.SecretFriendList
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

//密友
object SecretFriend {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val str = LocalizedStrings
    private val pkg = WechatPackage

    fun changeUserStatusByUsername(context: Context, username: String?, isSecret: Boolean) {
        if (username == null) {
            Toast.makeText(
                    context, str[PROMPT_USER_NOT_FOUND], Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (isSecret) {
            SecretFriendList += username
        } else {
            SecretFriendList -= username
        }
        pkg.AddressAdapterObject.get()?.notifyDataSetChanged()
        pkg.ConversationAdapterObject.get()?.notifyDataSetChanged()
    }

    fun changeUserStatusByNickname(context: Context, nickname: String?, isSecret: Boolean) {
        if (nickname == null) {
            return
        }
        val username = getContactByNickname(nickname)?.username
        changeUserStatusByUsername(context, username, isSecret)
    }

    fun onAdapterCreated(param: XC_MethodHook.MethodHookParam) {
        if (!preferences!!.getBoolean(SETTINGS_SECRET_FRIEND, false)) {
            return
        }
        val adapter = param.thisObject as BaseAdapter
        AdapterHider.register(adapter, "Secret Friend", { item ->
            val username = getObjectField(item, "field_username")
            username in SecretFriendList
        })
    }

    @JvmStatic fun hideChattingWindow() {
        findAndHookMethod(pkg.ChattingUI, "onCreate", C.Bundle, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!preferences!!.getBoolean(SETTINGS_SECRET_FRIEND, false)) {
                    return
                }

                val activity = param.thisObject as Activity
                val username = activity.intent.getStringExtra("Chat_User")
                if (username in SecretFriendList) {
                    Toast.makeText(
                            activity, str[PROMPT_USER_NOT_FOUND], Toast.LENGTH_SHORT
                    ).show()
                    activity.finish()
                }
            }
        })
    }
}
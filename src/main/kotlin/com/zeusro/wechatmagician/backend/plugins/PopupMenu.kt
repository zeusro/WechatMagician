package com.zeusro.wechatmagician.backend.plugins

import android.app.Activity
import android.view.ContextMenu
import android.view.View
import android.widget.AdapterView
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.Global.ITEM_ID_BUTTON_CLEAN_UNREAD
import com.zeusro.wechatmagician.Global.ITEM_ID_BUTTON_HIDE_CHATROOM
import com.zeusro.wechatmagician.Global.ITEM_ID_BUTTON_HIDE_FRIEND
import com.zeusro.wechatmagician.Global.SETTINGS_CHATTING_CHATROOM_HIDER
import com.zeusro.wechatmagician.Global.SETTINGS_MARK_ALL_AS_READ
import com.zeusro.wechatmagician.Global.SETTINGS_SECRET_FRIEND
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.LocalizedStrings.BUTTON_CLEAN_UNREAD
import com.zeusro.wechatmagician.storage.LocalizedStrings.BUTTON_HIDE_CHATROOM
import com.zeusro.wechatmagician.storage.LocalizedStrings.BUTTON_HIDE_FRIEND
import com.zeusro.wechatmagician.storage.Preferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.*

// app 组件
object PopupMenu {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val str = LocalizedStrings
    private val pkg = WechatPackage

    @Volatile var current_username: String? = null

    @JvmStatic fun addMenuItemsForContacts() {
        findAndHookMethod(
                pkg.ContactLongClickListener, "onItemLongClick",
                C.AdapterView, C.View, C.Int, C.Long, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val parent = param.args[0] as AdapterView<*>
                val position = param.args[2] as Int
                val item = parent.adapter?.getItem(position)
                current_username = getObjectField(item, "field_username") as String?
            }
        })

        findAndHookMethod(
                pkg.AddressUI, "onCreateContextMenu",
                C.ContextMenu, C.View, C.ContextMenuInfo, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val menu = param.args[0] as ContextMenu
                val view = param.args[1] as View
                if (preferences!!.getBoolean(SETTINGS_SECRET_FRIEND, false)) {
                    val item = menu.add(0, ITEM_ID_BUTTON_HIDE_FRIEND, 0, str[BUTTON_HIDE_FRIEND])
                    item.setOnMenuItemClickListener {
                        SecretFriend.changeUserStatusByUsername(view.context, current_username, true)
                        return@setOnMenuItemClickListener true
                    }
                }
            }
        })

        findAndHookMethod(pkg.MMListPopupWindow, "show", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val listenerField = findFirstFieldByExactType(pkg.MMListPopupWindow, C.AdapterView_OnItemClickListener)
                val listener = listenerField.get(param.thisObject) as AdapterView.OnItemClickListener
                listenerField.set(param.thisObject, AdapterView.OnItemClickListener { parent, view, position, id ->
                    val item = parent.adapter.getItem(position)
                    when (item) {
                        str[BUTTON_HIDE_FRIEND] -> {
                            if (preferences!!.getBoolean(SETTINGS_SECRET_FRIEND, false)) {
                                SecretFriend.changeUserStatusByUsername(view.context, current_username, true)
                                XposedHelpers.callMethod(param.thisObject, "dismiss")
                            }
                        }
                        else ->
                            listener.onItemClick(parent, view, position, id)
                    }
                })
            }
        })
    }

    @JvmStatic fun addMenuItemsForConversations() {
        findAndHookMethod(
                pkg.ConversationLongClickListener, "onItemLongClick",
                C.AdapterView, C.View, C.Int, C.Long, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val parent = param.args[0] as AdapterView<*>
                val position = param.args[2] as Int
                val item = parent.adapter?.getItem(position)
                current_username = getObjectField(item, "field_username") as String?
            }
        })

        findAndHookMethod(
                pkg.ConversationCreateContextMenuListener, "onCreateContextMenu",
                C.ContextMenu, C.View, C.ContextMenuInfo, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val menu = param.args[0] as ContextMenu
                if (preferences!!.getBoolean(SETTINGS_CHATTING_CHATROOM_HIDER, false)) {
                    if (current_username?.endsWith("@chatroom") == true) {
                        val item = menu.add(0, ITEM_ID_BUTTON_HIDE_CHATROOM, 0, str[BUTTON_HIDE_CHATROOM])
                        item.setOnMenuItemClickListener {
                            ChatroomHider.changeChatroomStatus(current_username, true)
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                if (preferences!!.getBoolean(SETTINGS_MARK_ALL_AS_READ, true)) {
                    val item = menu.add(0, ITEM_ID_BUTTON_CLEAN_UNREAD, 0, str[BUTTON_CLEAN_UNREAD])
                    item.setOnMenuItemClickListener {
                        OneClick.cleanUnreadCount(param.thisObject as? Activity)
                        return@setOnMenuItemClickListener true
                    }
                }
            }
        })

        findAndHookMethod(pkg.MMListPopupWindow, "show", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val listenerField = findFirstFieldByExactType(pkg.MMListPopupWindow, C.AdapterView_OnItemClickListener)
                val listener = listenerField.get(param.thisObject) as AdapterView.OnItemClickListener
                listenerField.set(param.thisObject, AdapterView.OnItemClickListener { parent, view, position, id ->
                    val item = parent.adapter.getItem(position)
                    when (item) {
                        str[BUTTON_HIDE_CHATROOM] -> {
                            if (preferences!!.getBoolean(SETTINGS_CHATTING_CHATROOM_HIDER, false)) {
                                ChatroomHider.changeChatroomStatus(current_username, true)
                                XposedHelpers.callMethod(param.thisObject, "dismiss")
                            }
                        }
                        str[BUTTON_CLEAN_UNREAD] -> {
                            if (preferences!!.getBoolean(SETTINGS_MARK_ALL_AS_READ, true)) {
                                val context = getObjectField(param.thisObject, "mContext")
                                OneClick.cleanUnreadCount(context as? Activity)
                                XposedHelpers.callMethod(param.thisObject, "dismiss")
                            }
                        }
                        else ->
                            listener.onItemClick(parent, view, position, id)
                    }
                })
            }
        })
    }
}
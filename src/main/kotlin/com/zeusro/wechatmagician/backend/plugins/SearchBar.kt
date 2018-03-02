package com.zeusro.wechatmagician.backend.plugins

import android.app.AlertDialog
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import com.zeusro.wechatmagician.Global.SETTINGS_CHATTING_CHATROOM_HIDER
import com.zeusro.wechatmagician.Global.SETTINGS_SECRET_FRIEND
import com.zeusro.wechatmagician.Global.SETTINGS_SECRET_FRIEND_PASSWORD
import com.zeusro.wechatmagician.Global.STATUS_FLAG_COMMAND
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.backend.plugins.SecretFriend.changeUserStatusByNickname
import com.zeusro.wechatmagician.backend.plugins.SecretFriend.changeUserStatusByUsername
import com.zeusro.wechatmagician.frontend.wechat.ConversationAdapter
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.LocalizedStrings.BUTTON_CANCEL
import com.zeusro.wechatmagician.storage.LocalizedStrings.PROMPT_SET_PASSWORD
import com.zeusro.wechatmagician.storage.LocalizedStrings.PROMPT_VERIFY_PASSWORD
import com.zeusro.wechatmagician.storage.LocalizedStrings.TITLE_SECRET_FRIEND
import com.zeusro.wechatmagician.storage.Preferences
import com.zeusro.wechatmagician.storage.list.SecretFriendList
import com.zeusro.wechatmagician.util.PasswordUtil
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors

// app 组件
object SearchBar {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val str = LocalizedStrings
    private val pkg = WechatPackage

    private fun handleCommand(context: Context, command: String): Boolean {
        when {
            command.startsWith("#alert ") -> {
                val prompt = command.drop("#alert ".length)
                AlertDialog.Builder(context)
                        .setTitle("Wechat Magician")
                        .setMessage(prompt)
                        .show()
                return true
            }
            command.startsWith("#chatrooms") -> {
                if (!preferences!!.getBoolean(SETTINGS_CHATTING_CHATROOM_HIDER, false)) {
                    return false
                }

                val adapter = ConversationAdapter(context)
                AlertDialog.Builder(context)
                        .setTitle("Wechat Magician")
                        .setAdapter(adapter, { _, _ -> })
                        .setNegativeButton(str[BUTTON_CANCEL], { dialog, _ ->
                            dialog.dismiss()
                        })
                        .show()
                return true
            }
            command.startsWith("#hide ") -> {
                if (!preferences!!.getBoolean(SETTINGS_SECRET_FRIEND, false)) {
                    return false
                }

                val encrypted = preferences!!.getString(SETTINGS_SECRET_FRIEND_PASSWORD, "")
                if (encrypted == "") {
                    Toast.makeText(
                            context, str[PROMPT_SET_PASSWORD], Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val nickname = command.drop("#hide ".length)
                    changeUserStatusByNickname(context, nickname, true)
                }
                return true
            }
            command.startsWith("#unhide ") -> {
                if (!preferences!!.getBoolean(SETTINGS_SECRET_FRIEND, false)) {
                    return false
                }

                val encrypted = preferences!!.getString(SETTINGS_SECRET_FRIEND_PASSWORD, "")
                if (encrypted == "") {
                    Toast.makeText(
                            context, str[PROMPT_SET_PASSWORD], Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val title = str[TITLE_SECRET_FRIEND]
                    val message = str[PROMPT_VERIFY_PASSWORD]
                    PasswordUtil.askPasswordWithVerify(context, title, message, encrypted) {
                        val nickname = command.drop("#unhide ".length)
                        if (nickname == "all") {
                            SecretFriendList.forEach { username ->
                                changeUserStatusByUsername(context, username, false)
                            }
                        } else {
                            changeUserStatusByNickname(context, nickname, false)
                        }
                    }
                }
                return true
            }
            else -> return false
        }
    }

    @JvmStatic fun hijackSearchBar() {
        hookAllConstructors(pkg.ActionBarEditText, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val search = param.thisObject as EditText
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                    override fun afterTextChanged(editable: Editable?) {
                        val command = editable.toString()
                        if (command.endsWith("#")) {
                            val consumed = handleCommand(search.context, command.dropLast(1))
                            if (consumed) {
                                val imm = search.context.getSystemService(INPUT_METHOD_SERVICE)
                                (imm as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
                                editable?.clear()
                            }
                        }
                    }
                })
            }
        })

        pkg.setStatus(STATUS_FLAG_COMMAND, true)
    }
}

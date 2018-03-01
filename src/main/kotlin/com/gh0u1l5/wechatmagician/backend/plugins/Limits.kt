package com.zeusro.wechatmagician.backend.plugins

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.TextView
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.Global.SETTINGS_SELECT_PHOTOS_LIMIT
import com.zeusro.wechatmagician.R
import com.zeusro.wechatmagician.backend.WechatEvents
import com.zeusro.wechatmagician.backend.WechatHook
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.LocalizedStrings.BUTTON_SELECT_ALL
import com.zeusro.wechatmagician.storage.Preferences
import com.zeusro.wechatmagician.util.PackageUtil.findAndHookMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod

// 突破发信息只能九张图片的设置
object Limits {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val str = LocalizedStrings
    private val pkg = WechatPackage
    private val events = WechatEvents

    // Hook AlbumPreviewUI to bypass the limit on number of selected photos.
    @JvmStatic fun breakSelectPhotosLimit() {
        findAndHookMethod(
                pkg.AlbumPreviewUI, "onCreate",
                C.Bundle, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val intent = (param.thisObject as Activity).intent ?: return
                val current = intent.getIntExtra("max_select_count", 9)
                val limit = try {
                    preferences!!.getString(
                            SETTINGS_SELECT_PHOTOS_LIMIT, "1000"
                    ).toInt()
                } catch (_: Throwable) { 1000 }
                if (current <= 9) {
                    intent.putExtra("max_select_count", current + limit - 9)
                }
            }
        })
    }

    @JvmStatic fun breakSelectContactLimit() {
        // Hook MMActivity.onCreateOptionsMenu to add "Select All" button.
        findAndHookMethod(
                pkg.MMActivity, "onCreateOptionsMenu",
                C.Menu, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.thisObject.javaClass != pkg.SelectContactUI) {
                    return
                }

                val menu = param.args[0] as Menu? ?: return
                val activity = param.thisObject as Activity
                val checked = activity.intent?.getBooleanExtra(
                        "select_all_checked", false
                ) ?: false

                val selectAll = menu.add(0, 2, 0, "")
                selectAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                if (WechatHook.MODULE_RES == null) {
                    selectAll.isChecked = checked
                    selectAll.title = str[BUTTON_SELECT_ALL] + "  " +
                            if (checked) "\u2611" else "\u2610"
                    selectAll.setOnMenuItemClickListener {
                        events.onSelectContactUISelectAll(activity, !selectAll.isChecked); true
                    }
                } else {
                    val layout = WechatHook.MODULE_RES?.getLayout(R.layout.wechat_checked_textview)
                    val checkedTextView = activity.layoutInflater.inflate(layout, null)
                    checkedTextView.findViewById<TextView>(R.id.ctv_text).apply {
                        setTextColor(Color.WHITE)
                        text = str[BUTTON_SELECT_ALL]
                    }
                    checkedTextView.findViewById<CheckBox>(R.id.ctv_checkbox).apply {
                        isChecked = checked
                        setOnCheckedChangeListener { _, checked ->
                            events.onSelectContactUISelectAll(activity, checked)
                        }
                    }
                    selectAll.actionView = checkedTextView
                }
            }
        })

        // Hook SelectContactUI to help the "Select All" button.
        findAndHookMethod(
                pkg.SelectContactUI, "onActivityResult",
                C.Int, C.Int, C.Intent, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val requestCode = param.args[0] as Int
                val resultCode = param.args[1] as Int
                val data = param.args[2] as Intent?

                if (requestCode == 5) {
                    val activity = param.thisObject as Activity
                    activity.setResult(resultCode, data)
                    activity.finish()
                    param.result = null
                }
            }
        })

        // Hook SelectContactUI to bypass the limit on number of recipients.
        findAndHookMethod(
                pkg.SelectContactUI, "onCreate",
                C.Bundle, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val intent = (param.thisObject as Activity).intent ?: return
                if (intent.getIntExtra("max_limit_num", -1) == 9) {
                    intent.putExtra("max_limit_num", 0x7FFFFFFF)
                }
            }
        })
    }

    // Hook SelectConversationUI to bypass the limit on number of recipients.
    @JvmStatic fun breakSelectConversationLimit() {
        findAndHookMethod(pkg.SelectConversationUI, pkg.SelectConversationUIMaxLimitMethod, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = false
            }
        })
    }
}
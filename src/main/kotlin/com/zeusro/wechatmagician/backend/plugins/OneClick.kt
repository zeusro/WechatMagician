package com.zeusro.wechatmagician.backend.plugins

import android.app.Activity
import android.content.Intent
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.storage.database.MainDatabase
import me.leolin.shortcutbadger.ShortcutBadger

// 一键标记所有聊天对话为已读
object OneClick {

    private val pkg = WechatPackage

    fun cleanUnreadCount(activity: Activity?) {
        if (activity == null) {
            return
        }

        MainDatabase.cleanUnreadCount()
        ShortcutBadger.removeCount(activity)
        activity.finish()
        activity.startActivity(Intent(activity, pkg.LauncherUI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
        })
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
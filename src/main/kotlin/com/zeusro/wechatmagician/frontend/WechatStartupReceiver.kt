package com.zeusro.wechatmagician.frontend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zeusro.wechatmagician.Global.ACTION_UPDATE_PREF
import com.zeusro.wechatmagician.Global.ACTION_WECHAT_STARTUP
import com.zeusro.wechatmagician.Global.FOLDER_SHARED_PREFS
import com.zeusro.wechatmagician.Global.MAGICIAN_BASE_DIR
import com.zeusro.wechatmagician.util.FileUtil
import java.io.File

// WechatStartupReceiver will fix the file permissions for shared preferences.
class WechatStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_WECHAT_STARTUP) {
            val baseDir = File(MAGICIAN_BASE_DIR)
            FileUtil.setWorldExecutable(baseDir)

            val sharedPrefsDir = File(baseDir, FOLDER_SHARED_PREFS)
            FileUtil.setWorldExecutable(sharedPrefsDir)

            sharedPrefsDir.listFiles().forEach { file ->
                FileUtil.setWorldReadable(file)
            }

            context.sendBroadcast(Intent().setAction(ACTION_UPDATE_PREF))
        }
    }
}
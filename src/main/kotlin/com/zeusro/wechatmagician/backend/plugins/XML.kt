package com.zeusro.wechatmagician.backend.plugins

import com.zeusro.wechatmagician.Global.SETTINGS_CHATTING_RECALL
import com.zeusro.wechatmagician.Global.SETTINGS_CHATTING_RECALL_PROMPT
import com.zeusro.wechatmagician.Global.SETTINGS_SNS_KEYWORD_BLACKLIST
import com.zeusro.wechatmagician.Global.SETTINGS_SNS_KEYWORD_BLACKLIST_CONTENT
import com.zeusro.wechatmagician.Global.STATUS_FLAG_XML_PARSER
import com.zeusro.wechatmagician.Global.tryWithThread
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.LocalizedStrings.PROMPT_RECALL
import com.zeusro.wechatmagician.storage.Preferences
import com.zeusro.wechatmagician.storage.cache.SnsCache
import com.zeusro.wechatmagician.storage.list.SnsBlacklist
import com.zeusro.wechatmagician.util.MessageUtil
import com.zeusro.wechatmagician.util.PackageUtil.findAndHookMethod
import de.robv.android.xposed.XC_MethodHook

object XML {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val str = LocalizedStrings
    private val pkg = WechatPackage

    @JvmStatic fun hookXMLParse() {
        // Hook XML Parser for the status bar easter egg.
        findAndHookMethod(pkg.XMLParserClass, pkg.XMLParseMethod, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                @Suppress("UNCHECKED_CAST")
                val result = param.result as MutableMap<String, String?>? ?: return
                if (result[".sysmsg.\$type"] == "revokemsg") {
                    handleRevokeCommand(result)
                }
                if (result[".TimelineObject"] != null) {
                    matchKeywordBlackList(result)
                    recordTimelineObject(result)
                }
            }
        })

        pkg.setStatus(STATUS_FLAG_XML_PARSER, true)
    }

    private fun handleRevokeCommand(result: MutableMap<String, String?>) {
        val msgTag = ".sysmsg.revokemsg.replacemsg"
        val msg = result[msgTag] ?: return
        if (!msg.startsWith("\"")) {
            return
        }
        if (!preferences!!.getBoolean(SETTINGS_CHATTING_RECALL, true)) {
            return
        }
        val prompt = preferences!!.getString(
                SETTINGS_CHATTING_RECALL_PROMPT, str[PROMPT_RECALL])
        //这里讲原本的内容替换为自定义的内容. "妄图撤回一条消息，啧啧"
        result[msgTag] = MessageUtil.applyEasterEgg(msg, prompt)
    }

    private fun matchKeywordBlackList(result: MutableMap<String, String?>) {
        if (!preferences!!.getBoolean(SETTINGS_SNS_KEYWORD_BLACKLIST, false)) {
            return
        }
        if (result[".TimelineObject.private"] == "1") {
            return
        }
        val content = result[".TimelineObject.contentDesc"] ?: return
        val list = preferences!!.getStringList(SETTINGS_SNS_KEYWORD_BLACKLIST_CONTENT, listOf())
        list.forEach {
            if (content.contains(it)) {
                SnsBlacklist += result[".TimelineObject.id"]
                result[".TimelineObject.private"] = "1"
                return
            }
        }
    }

    private fun recordTimelineObject(result: MutableMap<String, String?>) {
        tryWithThread {
            val id = result[".TimelineObject.id"]
            if (id != null) {
                SnsCache[id] = SnsCache.SnsInfo(result)
            }
        }
    }
}
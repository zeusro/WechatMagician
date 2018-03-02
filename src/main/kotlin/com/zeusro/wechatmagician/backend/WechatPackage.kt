package com.zeusro.wechatmagician.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.BaseAdapter
import com.zeusro.wechatmagician.BuildConfig
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.Global.ACTION_REQUIRE_HOOK_STATUS
import com.zeusro.wechatmagician.Global.ACTION_REQUIRE_WECHAT_PACKAGE
import com.zeusro.wechatmagician.Global.tryOrNull
import com.zeusro.wechatmagician.Global.tryWithLog
import com.zeusro.wechatmagician.Global.tryWithThread
import com.zeusro.wechatmagician.Version
import com.zeusro.wechatmagician.WaitChannel
import com.zeusro.wechatmagician.backend.plugins.ChatroomHider
import com.zeusro.wechatmagician.backend.plugins.SecretFriend
import com.zeusro.wechatmagician.util.PackageUtil
import com.zeusro.wechatmagician.util.PackageUtil.findClassIfExists
import com.zeusro.wechatmagician.util.PackageUtil.findClassesFromPackage
import com.zeusro.wechatmagician.util.PackageUtil.findFieldsWithGenericType
import com.zeusro.wechatmagician.util.PackageUtil.findFieldsWithType
import com.zeusro.wechatmagician.util.PackageUtil.findMethodsByExactParameters
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import net.dongliu.apk.parser.ApkFile
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// WechatPackage analyzes and stores critical classes and objects in Wechat application.
// These classes and objects will be used for hooking and tampering with runtime data.
object WechatPackage {

    // initializeChannel resumes all the thread waiting for the WechatPackage initialization.
    private val initializeChannel = WaitChannel()

    // status stores the working status of all the hooks.
    private val statusLock = ReentrantReadWriteLock()
    private val status: HashMap<String, Boolean> = hashMapOf()

    // These stores necessary information to match signatures.
    @Volatile var packageName: String = ""
    @Volatile var loader: ClassLoader? = null
    @Volatile var version: Version? = null
    @Volatile var classes: List<String>? = null

    // These are the cache of important global objects
    @Volatile var AddressAdapterObject: WeakReference<BaseAdapter?> = WeakReference(null)
    @Volatile var ConversationAdapterObject: WeakReference<BaseAdapter?> = WeakReference(null)
    @Volatile var MsgStorageObject: Any? = null
    @Volatile var ImgStorageObject: Any? = null

    private fun <T> innerLazy(name: String, initializer: () -> T?): Lazy<T> = lazy {
        initializeChannel.wait()
        initializer() ?: throw Error("Failed to evaluate $name")
    }

    private val WECHAT_PACKAGE_SQLITE: String by innerLazy("WECHAT_PACKAGE_SQLITE") {
        when {
            version!! >= Version("6.5.8") -> "com.tencent.wcdb"
            else -> "com.tencent.mmdb"
        }
    }
    private val WECHAT_PACKAGE_UI: String         by lazy { "$packageName.ui" }
    private val WECHAT_PACKAGE_SNS_UI: String     by lazy { "$packageName.plugin.sns.ui" }
    private val WECHAT_PACKAGE_GALLERY_UI: String by lazy { "$packageName.plugin.gallery.ui" }

    val LogCat: Class<*> by innerLazy("LogCat") {
        findClassesFromPackage(loader!!, classes!!, "$packageName.sdk.platformtools")
                .filterByEnclosingClass(null)
                .filterByMethod(C.Int, "getLogLevel")
                .firstOrNull()
    }
    val WebWXLoginUI: Class<*> by innerLazy("WebWXLoginUI") {
        findClassIfExists("$packageName.plugin.webwx.ui.ExtDeviceWXLoginUI", loader)
    }
    val RemittanceAdapter: Class<*> by innerLazy("RemittanceAdapter") {
        findClassIfExists("$packageName.plugin.remittance.ui.RemittanceAdapterUI", loader)
    }
    val ActionBarEditText: Class<*> by innerLazy("ActionBarEditText") {
        findClassIfExists("$packageName.ui.tools.ActionBarSearchView.ActionBarEditText", loader)
    }

    val WXCustomScheme: Class<*> by innerLazy("WXCustomScheme") {
        findClassIfExists("$packageName.plugin.base.stub.WXCustomSchemeEntryActivity", loader)
    }
    val WXCustomSchemeEntryMethod: Method by innerLazy("WXCustomSchemeEntryMethod") {
        findMethodsByExactParameters(WXCustomScheme, C.Boolean, C.Intent).firstOrNull()
    }

    val EncEngine: Class<*> by innerLazy("EncEngine") {
        findClassesFromPackage(loader!!, classes!!, "$packageName.modelsfs")
                .filterByMethod(null, "seek", C.Long)
                .filterByMethod(null, "free")
                .firstOrNull()
    }
    val EncEngineEDMethod: Method by innerLazy("EncEngineEDMethod") {
        findMethodsByExactParameters(EncEngine, C.Int, C.ByteArray, C.Int).firstOrNull()
    }

    val SQLiteDatabase: Class<*> by innerLazy("SQLiteDatabase") {
        findClassIfExists("$WECHAT_PACKAGE_SQLITE.database.SQLiteDatabase", loader)
    }
    val SQLiteCursorFactory: Class<*> by innerLazy("SQLiteCursorFactory") {
        findClassIfExists("$WECHAT_PACKAGE_SQLITE.database.SQLiteDatabase.CursorFactory", loader)
    }
    val SQLiteErrorHandler: Class<*> by innerLazy("SQLiteErrorHandler") {
        findClassIfExists("$WECHAT_PACKAGE_SQLITE.DatabaseErrorHandler", loader)
    }
    val SQLiteCancellationSignal: Class<*> by innerLazy("SQLiteCancellationSignal") {
        findClassIfExists("$WECHAT_PACKAGE_SQLITE.support.CancellationSignal", loader)
    }

    val LauncherUI: Class<*> by innerLazy("LauncherUI") {
        findClassIfExists("$WECHAT_PACKAGE_UI.LauncherUI", loader)
    }
    val MMActivity: Class<*> by innerLazy("MMActivity") {
        findClassIfExists("$WECHAT_PACKAGE_UI.MMActivity", loader)
    }
    val MMFragmentActivity: Class<*> by innerLazy("MMFragmentActivity") {
        findClassIfExists("$WECHAT_PACKAGE_UI.MMFragmentActivity", loader)
    }
    val MMListPopupWindow: Class<*> by innerLazy("MMListPopupWindow") {
        findClassIfExists("$WECHAT_PACKAGE_UI.base.MMListPopupWindow", loader)
    }

    val BaseAdapter: Class<*> by innerLazy("BaseAdapter") {
        findClassIfExists("android.widget.BaseAdapter", loader)
    }
    val HeaderViewListAdapter: Class<*> by innerLazy("HeaderViewListAdapter") {
        findClassIfExists("android.widget.HeaderViewListAdapter", loader)
    }
    val MMBaseAdapter: Class<*> by innerLazy("MMBaseAdapter") {
        val addressBase = AddressAdapter.superclass
        val conversationBase = ConversationWithCacheAdapter.superclass
        if (addressBase != conversationBase) {
            log("Unexpected base adapter: $addressBase and $conversationBase")
        }
        return@innerLazy addressBase
    }
    val MMBaseAdapterGetMethod: String by innerLazy("MMBaseAdapterGetMethod") {
        MMBaseAdapter.declaredMethods.filter {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == C.Int
        }.firstOrNull {
            it.name != "getItem" && it.name != "getItemId"
        }?.name
    }
    val AddressAdapter: Class<*> by innerLazy("AddressAdapter") {
        findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.contact")
                .filterByMethod(null, "pause")
                .firstOrNull()
    }
    val ConversationWithCacheAdapter: Class<*> by innerLazy("ConversationWithCacheAdapter") {
        findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.conversation")
                .filterByMethod(null, "clearCache")
                .firstOrNull()
    }

    val AddressUI: Class<*> by innerLazy("AddressUI") {
        findClassIfExists("$WECHAT_PACKAGE_UI.contact.AddressUI.a", loader)
    }
    val ContactLongClickListener: Class<*> by innerLazy("ContactLongClickListener") {
        findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.contact")
                .filterByEnclosingClass(AddressUI)
                .filterByMethod(C.Boolean, "onItemLongClick", C.AdapterView, C.View, C.Int, C.Long)
                .firstOrNull()
    }
    val MainUI: Class<*> by innerLazy("MainUI") {
        findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.conversation")
                .filterByMethod(C.Int, "getLayoutId")
                .filterByMethod(null, "onConfigurationChanged", C.Configuration)
                .firstOrNull()
    }
    val ConversationLongClickListener: Class<*> by innerLazy("ConversationLongClickListener") {
        when {
            version!! >= Version("6.5.8") ->
                findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.conversation")
                        .filterByMethod(null, "onCreateContextMenu", C.ContextMenu, C.View, C.ContextMenuInfo)
                        .filterByMethod(C.Boolean, "onItemLongClick", C.AdapterView, C.View, C.Int, C.Long)
                        .firstOrNull()
            else ->
                findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.conversation")
                        .filterByEnclosingClass(MainUI)
                        .filterByMethod(C.Boolean, "onItemLongClick", C.AdapterView, C.View, C.Int, C.Long)
                        .firstOrNull()
        }
    }
    val ConversationCreateContextMenuListener: Class<*> by innerLazy("ConversationCreateContextMenuListener") {
        when {
            version!! >= Version("6.5.8") -> ConversationLongClickListener
            else -> MainUI
        }
    }
    val ChattingUI: Class<*> by innerLazy("ChattingUI") {
        findClassesFromPackage(loader!!, classes!!, "$WECHAT_PACKAGE_UI.chatting")
                .filterBySuper(MMFragmentActivity)
                .filterByMethod(null, "onRequestPermissionsResult", C.Int, C.StringArray, C.IntArray)
                .firstOrNull()
    }

    val SnsActivity: Class<*> by innerLazy("SnsActivity") {
        findClassesFromPackage(loader!!, classes!!, WECHAT_PACKAGE_SNS_UI)
                .filterByField("$WECHAT_PACKAGE_UI.base.MMPullDownView")
                .firstOrNull()
    }
    val SnsUploadUI: Class<*> by innerLazy("SnsUploadUI") {
        findClassesFromPackage(loader!!, classes!!, WECHAT_PACKAGE_SNS_UI)
                .filterByField("$WECHAT_PACKAGE_SNS_UI.LocationWidget")
                .filterByField("$WECHAT_PACKAGE_SNS_UI.SnsUploadSayFooter")
                .firstOrNull()
    }
    val SnsUploadUIEditTextField: String by innerLazy("SnsUploadUIEditTextField") {
        findFieldsWithType(
                SnsUploadUI, "$WECHAT_PACKAGE_SNS_UI.SnsEditText"
        ).firstOrNull()?.name ?: ""
    }
    val SnsUserUI: Class<*> by innerLazy("SnsUserUI") {
        findClassIfExists("$WECHAT_PACKAGE_SNS_UI.SnsUserUI", loader)
    }
    val SnsTimeLineUI: Class<*> by innerLazy("SnsTimeLineUI") {
        findClassesFromPackage(loader!!, classes!!, WECHAT_PACKAGE_SNS_UI)
                .filterByField("android.support.v7.app.ActionBar")
                .firstOrNull()
    }

    val AlbumPreviewUI: Class<*> by innerLazy("AlbumPreviewUI") {
        findClassIfExists("$WECHAT_PACKAGE_GALLERY_UI.AlbumPreviewUI", loader)
    }
    val SelectContactUI: Class<*> by innerLazy("SelectContactUI") {
        findClassIfExists("$WECHAT_PACKAGE_UI.contact.SelectContactUI", loader)
    }
    val SelectConversationUI: Class<*> by innerLazy("SelectConversationUI") {
        findClassIfExists("$WECHAT_PACKAGE_UI.transmit.SelectConversationUI", loader)
    }
    val SelectConversationUIMaxLimitMethod: Method by innerLazy("SelectConversationUIMaxLimitMethod") {
        findMethodsByExactParameters(SelectConversationUI, C.Boolean, C.Boolean).firstOrNull()
    }

    val MsgInfoClass: Class<*> by innerLazy("MsgInfoClass") {
        findClassesFromPackage(loader!!, classes!!, "$packageName.storage")
                .filterByMethod(C.Boolean, "isSystem")
                .firstOrNull()
    }
    val ContactInfoClass: Class<*> by innerLazy("ContactInfoClass") {
        findClassesFromPackage(loader!!, classes!!, "$packageName.storage")
                .filterByMethod(C.String, "getCityCode")
                .filterByMethod(C.String, "getCountryCode")
                .firstOrNull()
    }

    val MsgStorageClass: Class<*> by innerLazy("MsgStorageClass") {
        when {
            version!! >= Version("6.5.8") ->
                findClassesFromPackage(loader!!, classes!!, "$packageName.storage")
                        .filterByMethod(C.Long, MsgInfoClass, C.Boolean)
                        .firstOrNull()
            else ->
                findClassesFromPackage(loader!!, classes!!, "$packageName.storage")
                        .filterByMethod(C.Long, MsgInfoClass)
                        .firstOrNull()
        }
    }
    val MsgStorageInsertMethod: Method by innerLazy("MsgStorageInsertMethod") {
        when {
            version!! >= Version("6.5.8") ->
                //微信6.5.8以上使用下列方法
                findMethodsByExactParameters(
                        MsgStorageClass, C.Long, MsgInfoClass, C.Boolean
                ).firstOrNull()
            else ->
                findMethodsByExactParameters(
                        MsgStorageClass, C.Long, MsgInfoClass
                ).firstOrNull()
        }
    }

    val CacheMapClass: String by lazy { "$packageName.a.f" }
    val CacheMapPutMethod = "k"

    val ImgStorageClass: Class<*> by innerLazy("ImgStorageClass") {
        findClassesFromPackage(loader!!, classes!!, packageName, 1)
                .filterByMethod(C.String, ImgStorageLoadMethod, C.String, C.String, C.String, C.Boolean)
                .firstOrNull()
    }
    val ImgStorageCacheField: String by innerLazy("ImgStorageCacheField") {
        findFieldsWithGenericType(
                ImgStorageClass, "$CacheMapClass<java.lang.String, android.graphics.Bitmap>"
        ).firstOrNull()?.name ?: ""
    }
    val ImgStorageLoadMethod = "a"

    val XMLParserClass: Class<*> by innerLazy("XMLParserClass") {
        findClassesFromPackage(loader!!, classes!!,"$packageName.sdk.platformtools")
                .filterByMethod(C.Map, C.String, C.String)
                .firstOrNull()
    }
    val XMLParseMethod: Method by innerLazy("XMLParseMethod") {
        findMethodsByExactParameters(
                XMLParserClass, C.Map, C.String, C.String
        ).firstOrNull()
    }

    @JvmStatic fun hookAdapters() {
        XposedBridge.hookAllConstructors(AddressAdapter, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val adapter = param.thisObject as? BaseAdapter
                AddressAdapterObject = WeakReference(adapter)
                SecretFriend.onAdapterCreated(param)
            }
        })
        XposedBridge.hookAllConstructors(ConversationWithCacheAdapter, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val adapter = param.thisObject as? BaseAdapter
                ConversationAdapterObject = WeakReference(adapter)
                SecretFriend.onAdapterCreated(param)
                ChatroomHider.onAdapterCreated(param)
            }
        })
    }

    // init initializes necessary information for static analysis.
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        tryWithThread {
            try {
                packageName = lpparam.packageName
                loader = lpparam.classLoader
                version = getVersion(lpparam)

                var apkFile: ApkFile? = null
                try {
                    apkFile = ApkFile(lpparam.appInfo.sourceDir)
                    classes = apkFile.dexClasses.map { clazz ->
                        PackageUtil.getClassName(clazz)
                    }
                } finally {
                    apkFile?.close()
                }
            } catch (t: Throwable) {
                // Ignore this one
            } finally {
                initializeChannel.done()
            }
        }
    }

    private val requireHookStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusLock.read {
                setResultExtras(Bundle().apply {
                    putSerializable("status", status)
                })
            }
        }
    }

    private val requireWechatPackageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            resultData = this@WechatPackage.toString()
        }
    }

    // listen returns debug output to the frontend.
    fun listen(context: Context) {
        tryWithLog {
            context.registerReceiver(requireHookStatusReceiver, IntentFilter(ACTION_REQUIRE_HOOK_STATUS))
            context.registerReceiver(requireWechatPackageReceiver, IntentFilter(ACTION_REQUIRE_WECHAT_PACKAGE))
        }
    }

    // getVersion returns the version of current package / application
    private fun getVersion(lpparam: XC_LoadPackage.LoadPackageParam): Version {
        val activityThreadClass = findClass("android.app.ActivityThread", null)
        val activityThread = callStaticMethod(activityThreadClass, "currentActivityThread")
        val context = callMethod(activityThread, "getSystemContext") as Context?
        val versionName = context?.packageManager?.getPackageInfo(lpparam.packageName, 0)?.versionName
        return Version(versionName ?: throw Error("Cannot get Wechat version"))
    }

    // setStatus updates current status of the Wechat hooks.
    fun setStatus(key: String, value: Boolean) {
        statusLock.write {
            status[key] = value
        }
    }

    override fun toString(): String {
        val body = tryOrNull {
            this.javaClass.declaredFields.filter { field ->
                when (field.name) {
                    "INSTANCE", "\$\$delegatedProperties",
                    "initializeChannel",
                    "status", "statusLock",
                    "packageName", "loader", "version", "classes",
                    "WECHAT_PACKAGE_SQLITE",
                    "WECHAT_PACKAGE_UI",
                    "WECHAT_PACKAGE_SNS_UI",
                    "WECHAT_PACKAGE_GALLERY_UI" -> false
                    else -> true
                }
            }.joinToString("\n") {
                it.isAccessible = true
                val key = it.name.removeSuffix("\$delegate")
                var value = it.get(this)
                if (value is WeakReference<*>) {
                    value = value.get()
                }
                "$key = $value"
            }
        }

        return """====================================================
Wechat Package: $packageName
Wechat Version: $version
Module Version: ${BuildConfig.VERSION_NAME}
${body?.removeSuffix("\n") ?: "Failed to generate report."}
===================================================="""
    }
}
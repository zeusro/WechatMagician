package com.zeusro.wechatmagician.backend.plugins

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.widget.ListAdapter
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.Global.DEVELOPER_DATABASE_DELETE
import com.zeusro.wechatmagician.Global.DEVELOPER_DATABASE_EXECUTE
import com.zeusro.wechatmagician.Global.DEVELOPER_DATABASE_INSERT
import com.zeusro.wechatmagician.Global.DEVELOPER_DATABASE_QUERY
import com.zeusro.wechatmagician.Global.DEVELOPER_DATABASE_UPDATE
import com.zeusro.wechatmagician.Global.DEVELOPER_TRACE_FILES
import com.zeusro.wechatmagician.Global.DEVELOPER_TRACE_LOGCAT
import com.zeusro.wechatmagician.Global.DEVELOPER_UI_DUMP_POPUP_MENU
import com.zeusro.wechatmagician.Global.DEVELOPER_UI_TOUCH_EVENT
import com.zeusro.wechatmagician.Global.DEVELOPER_UI_TRACE_ACTIVITIES
import com.zeusro.wechatmagician.Global.DEVELOPER_XML_PARSER
import com.zeusro.wechatmagician.backend.WechatPackage
import com.zeusro.wechatmagician.storage.Preferences
import com.zeusro.wechatmagician.util.MessageUtil.argsToString
import com.zeusro.wechatmagician.util.MessageUtil.bundleToString
import com.zeusro.wechatmagician.util.PackageUtil.findAndHookMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import java.io.File

// 开发者模式
object Developer {

    private var preferences: Preferences? = null

    @JvmStatic fun init(_preferences: Preferences) {
        preferences = _preferences
    }

    private val pkg = WechatPackage

    // Hook View.onTouchEvent to trace touch events.
    @JvmStatic fun traceTouchEvents() {
        if (preferences!!.getBoolean(DEVELOPER_UI_TOUCH_EVENT, false)) {
            findAndHookMethod(
                    "android.view.View", pkg.loader,
                    "onTouchEvent", C.MotionEvent, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    log("View.onTouchEvent => obj.class = ${param.thisObject.javaClass}")
                }
            })
        }
    }

    // Hook Activity.startActivity and Activity.onCreate to trace activities.
    @JvmStatic fun traceActivities() {
        if (preferences!!.getBoolean(DEVELOPER_UI_TRACE_ACTIVITIES, false)) {
            findAndHookMethod(
                    "android.app.Activity", pkg.loader,
                    "startActivity", C.Intent, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as Intent?
                    log("Activity.startActivity => " +
                            "${param.thisObject.javaClass}, " +
                            "intent => ${bundleToString(intent?.extras)}")
                }
            })

            findAndHookMethod(
                    "android.app.Activity", pkg.loader,
                    "onCreate", C.Bundle, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val bundle = param.args[0] as Bundle?
                    val intent = (param.thisObject as Activity).intent
                    log("Activity.onCreate => " +
                            "${param.thisObject.javaClass}, " +
                            "intent => ${bundleToString(intent?.extras)}, " +
                            "bundle => ${bundleToString(bundle)}")
                }
            })
        }
    }

    // Hook MMListPopupWindow to trace every popup menu.
    @JvmStatic fun dumpPopupMenu() {
        if (preferences!!.getBoolean(DEVELOPER_UI_DUMP_POPUP_MENU, false)) {
            hookAllConstructors(pkg.MMListPopupWindow, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.thisObject
                    val context = param.args[0]
                    log("POPUP => menu.class = ${menu.javaClass}")
                    log("POPUP => context.class = ${context.javaClass}")
                }
            })

            findAndHookMethod(
                    pkg.MMListPopupWindow, "setAdapter",
                    C.ListAdapter, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val adapter = param.args[0] as ListAdapter? ?: return
                    log("POPUP => adapter.count = ${adapter.count}")
                    (0 until adapter.count).forEach { index ->
                        log("POPUP => adapter.item[$index] = ${adapter.getItem(index)}")
                        log("POPUP => adapter.item[$index].class = ${adapter.getItem(index).javaClass}")
                    }
                }
            })
        }
    }

    // Hook SQLiteDatabase to trace all the database operations.
    @JvmStatic fun traceDatabase() {
        if (preferences!!.getBoolean(DEVELOPER_DATABASE_QUERY, false)) {
            findAndHookMethod(
                    pkg.SQLiteDatabase, "rawQueryWithFactory",
                    pkg.SQLiteCursorFactory, C.String, C.StringArray, C.String, pkg.SQLiteCancellationSignal, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sql = param.args[1] as String?
                    val selectionArgs = param.args[2] as Array<*>?
                    log("DB => query sql = $sql, selectionArgs = ${argsToString(selectionArgs)}, db = ${param.thisObject}")
                }
            })
        }

        if (preferences!!.getBoolean(DEVELOPER_DATABASE_INSERT, false)) {
            findAndHookMethod(
                    pkg.SQLiteDatabase, "insertWithOnConflict",
                    C.String, C.String, C.ContentValues, C.Int, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as String?
                    val values = param.args[2] as ContentValues?
                    log("DB => insert table = $table, values = $values, db = ${param.thisObject}")
                }
            })
        }

        if (preferences!!.getBoolean(DEVELOPER_DATABASE_UPDATE, false)) {
            findAndHookMethod(
                    pkg.SQLiteDatabase, "updateWithOnConflict",
                    C.String, C.ContentValues, C.String, C.StringArray, C.Int, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as String?
                    val values = param.args[1] as ContentValues?
                    val whereClause = param.args[2] as String?
                    val whereArgs = param.args[3] as Array<*>?
                    log("DB => update " +
                            "table = $table, " +
                            "values = $values, " +
                            "whereClause = $whereClause, " +
                            "whereArgs = ${argsToString(whereArgs)}, " +
                            "db = ${param.thisObject}")
                }
            })
        }

        if (preferences!!.getBoolean(DEVELOPER_DATABASE_DELETE, false)) {
            findAndHookMethod(
                    pkg.SQLiteDatabase, "delete",
                    C.String, C.String, C.StringArray, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as String?
                    val whereClause = param.args[1] as String?
                    val whereArgs = param.args[2] as Array<*>?
                    log("DB => delete " +
                            "table = $table, " +
                            "whereClause = $whereClause, " +
                            "whereArgs = ${argsToString(whereArgs)}, " +
                            "db = ${param.thisObject}")
                }
            })
        }

        if (preferences!!.getBoolean(DEVELOPER_DATABASE_EXECUTE, false)) {
            findAndHookMethod(
                    pkg.SQLiteDatabase, "executeSql",
                    C.String, C.ObjectArray, pkg.SQLiteCancellationSignal, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sql = param.args[0] as String?
                    val bindArgs = param.args[1] as Array<*>?
                    log("DB => executeSql sql = $sql, bindArgs = ${argsToString(bindArgs)}, db = ${param.thisObject}")
                }
            })
        }
    }

    // Hook Log to trace hidden logcat output.
    @JvmStatic fun traceLogCat() {
        if (preferences!!.getBoolean(DEVELOPER_TRACE_LOGCAT, false)) {
            val functions = listOf("d", "e", "f", "i", "v", "w")
            functions.forEach { func ->
                findAndHookMethod(
                        pkg.LogCat, func,
                        C.String, C.String, C.ObjectArray, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val tag = param.args[0] as String?
                        val msg = param.args[1] as String?
                        val args = param.args[2] as Array<*>?
                        if (args == null) {
                            log("LOG.${func.toUpperCase()} => [$tag] $msg")
                        } else {
                            log("LOG.${func.toUpperCase()} => [$tag] ${msg?.format(*args)}")
                        }
                    }
                })
            }
        }
    }

    // Hook FileInputStream / FileOutputStream to trace file operations.
    @JvmStatic fun traceFiles() {
        if (preferences!!.getBoolean(DEVELOPER_TRACE_FILES, false)) {
            findAndHookConstructor(
                    "java.io.FileInputStream", pkg.loader,
                    C.File, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.args[0] as File?)?.absolutePath ?: return
                    log("FILE => Read $path")
                }
            })

            findAndHookConstructor(
                    "java.io.FileOutputStream", pkg.loader,
                    C.File, C.Boolean, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.args[0] as File?)?.absolutePath ?: return
                    log("FILE => Write $path")
                }
            })

            findAndHookMethod(
                    "java.io.File", pkg.loader, "delete", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val file = param.thisObject as File
                    log("FILE => Delete ${file.absolutePath}")
                }
            })
        }
    }

    // Hook XML Parser to trace the XML files used in Wechat.
    @JvmStatic fun traceXMLParse() {
        if (preferences!!.getBoolean(DEVELOPER_XML_PARSER, false)) {
            findAndHookMethod(pkg.XMLParserClass, pkg.XMLParseMethod, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val xml = param.args[0] as String?
                    val tag = param.args[1] as String?
                    log("XML => xml = $xml, tag = $tag")
                }
            })
        }
    }
}
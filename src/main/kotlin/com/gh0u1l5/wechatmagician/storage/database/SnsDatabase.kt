package com.zeusro.wechatmagician.storage.database

import com.zeusro.wechatmagician.util.MessageUtil.longToDecimalString
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod

object SnsDatabase {
    // snsDB is the database that stores SNS information.
    @Volatile var snsDB: Any? = null

    // getSnsIdFromRowId searches the database to find the snsId in a specific row
    fun getSnsIdFromRowId(rowId: String?): String? {
        if (rowId == null || rowId == "") {
            return null
        }

        val database = snsDB ?: return null
        var cursor: Any? = null
        try {
            cursor = callMethod(database, "query",
                    "SnsInfo", arrayOf("snsId"), "rowId=?", arrayOf(rowId),
                    null, null, null, null
            )
            val count = callMethod(cursor, "getCount")
            if (count != 1) {
                XposedBridge.log("DB => Unexpected count $count for rowId $rowId in table SnsInfo")
                return null
            }
            callMethod(cursor, "moveToFirst")
            val snsId = XposedHelpers.callMethod(cursor, "getLong", 0)
            return longToDecimalString(snsId as Long)
        } catch (t: Throwable) {
            log(t); return null
        } finally {
            if (cursor != null) {
                callMethod(cursor, "close")
            }
        }
    }
}

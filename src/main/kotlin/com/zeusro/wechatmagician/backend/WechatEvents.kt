package com.zeusro.wechatmagician.backend

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.*
import com.zeusro.wechatmagician.C
import com.zeusro.wechatmagician.backend.plugins.ChatroomHider.changeChatroomStatus
import com.zeusro.wechatmagician.backend.plugins.SnsForward.ForwardAsyncTask
import com.zeusro.wechatmagician.frontend.wechat.ConversationAdapter
import com.zeusro.wechatmagician.frontend.wechat.ListPopupWindowPosition
import com.zeusro.wechatmagician.frontend.wechat.StringListAdapter
import com.zeusro.wechatmagician.storage.LocalizedStrings
import com.zeusro.wechatmagician.storage.LocalizedStrings.MENU_CHATROOM_UNHIDE
import com.zeusro.wechatmagician.storage.LocalizedStrings.MENU_SNS_FORWARD
import com.zeusro.wechatmagician.storage.LocalizedStrings.MENU_SNS_SCREENSHOT
import com.zeusro.wechatmagician.storage.LocalizedStrings.PROMPT_SCREENSHOT
import com.zeusro.wechatmagician.storage.LocalizedStrings.PROMPT_WAIT
import com.zeusro.wechatmagician.util.FileUtil
import com.zeusro.wechatmagician.util.ImageUtil
import com.zeusro.wechatmagician.util.ViewUtil
import com.zeusro.wechatmagician.util.ViewUtil.dp2px
import de.robv.android.xposed.XposedHelpers.findFirstFieldByExactType
import java.lang.reflect.Field

object WechatEvents {

    private val str = LocalizedStrings
    private val pkg = WechatPackage

    // Handle the logic about "select all" check box in SelectContactUI
    fun onSelectContactUISelectAll(activity: Activity, isChecked: Boolean) {
        val intent = activity.intent ?: return
        intent.putExtra("select_all_checked", isChecked)
        intent.putExtra("already_select_contact", "")
        if (isChecked) {
            // Search for the ListView of contacts
            val listView = findFirstFieldByExactType(activity.javaClass, C.ListView)
                    .get(activity) as ListView? ?: return
            val adapter = (listView.adapter as HeaderViewListAdapter).wrappedAdapter

            // Construct the list of user names
            var contactField: Field? = null
            var usernameField: Field? = null
            val userList = mutableListOf<String>()
            repeat(adapter.count, next@ { index ->
                val item = adapter.getItem(index)

                if (contactField == null) {
                    contactField = item.javaClass.fields.firstOrNull {
                        it.type.name == pkg.ContactInfoClass.name
                    } ?: return@next
                }
                val contact = contactField?.get(item) ?: return@next

                if (usernameField == null) {
                    usernameField = contact.javaClass.fields.firstOrNull {
                        it.name == "field_username"
                    } ?: return@next
                }
                val username = usernameField?.get(contact) ?: return@next
                userList.add(username as String)
            })
            intent.putExtra("already_select_contact", userList.joinToString(","))
        }
        activity.startActivityForResult(intent, 5)
    }

    // Show a popup menu in SnsTimelineUI
    fun onTimelineItemLongClick(parent: AdapterView<*>, view: View, snsId: Long, position: ListPopupWindowPosition?): Boolean {
        val operations = listOf(str[MENU_SNS_FORWARD], str[MENU_SNS_SCREENSHOT])
        ListPopupWindow(parent.context).apply {
            if (position != null) {
                // Calculate list view size
                val location = IntArray(2)
                position.anchor.getLocationOnScreen(location)
                val bottom = location[1] + position.anchor.height

                // Set position for popup window
                anchorView = position.anchor
                horizontalOffset = position.x - position.anchor.left
                verticalOffset = position.y - bottom
            } else {
                anchorView = view
            }

            // Set general properties for popup window
            width = parent.context.dp2px(120)
            setDropDownGravity(Gravity.CENTER)
            setAdapter(StringListAdapter(view.context, operations))
            setOnItemClickListener { _, _, operation, _ ->
                onTimelineItemPopupMenuSelected(view, snsId, operation)
                dismiss()
            }
        }.show()
        return true
    }

    // Handle the logic about the popup menu in SnsTimelineUI
    private fun onTimelineItemPopupMenuSelected(itemView: View, snsId: Long, operation: Int): Boolean {
        when (operation) {
            0 -> { // Forward
                ForwardAsyncTask(snsId, itemView.context).execute()
                Toast.makeText(
                        itemView.context, str[PROMPT_WAIT], Toast.LENGTH_SHORT
                ).show()
                return true
            }
            1 -> { // Screenshot
                val path = ImageUtil.createScreenshotPath()
                val bitmap = ViewUtil.drawView(itemView)
                FileUtil.writeBitmapToDisk(path, bitmap)
                FileUtil.notifyNewMediaFile(path, itemView.context)
                Toast.makeText(
                        itemView.context, str[PROMPT_SCREENSHOT] + path, Toast.LENGTH_SHORT
                ).show()
                return true
            }
        }
        return false
    }

    fun onChatroomHiderConversationClick(view: View, username: String): Boolean {
        view.context.startActivity(Intent(view.context, pkg.ChattingUI)
                .putExtra("Chat_Mode", 1)
                .putExtra("Chat_User", username))
        return true
    }

    fun onChatroomHiderConversationLongClick(view: View, adapter: ConversationAdapter, username: String): Boolean {
        val operations = listOf(str[MENU_CHATROOM_UNHIDE])
        ListPopupWindow(view.context).apply {
            anchorView = view
            width = view.context.dp2px(140)
            setDropDownGravity(Gravity.CENTER)
            setAdapter(StringListAdapter(view.context, operations))
            setOnItemClickListener { _, _, operation, _ ->
                onChatroomHiderConversationPopupMenuSelected(adapter, username, operation)
                dismiss()
            }
        }.show()
        return true
    }

    private fun onChatroomHiderConversationPopupMenuSelected(adapter: ConversationAdapter, username: String, operation: Int): Boolean {
        when (operation) {
            0 -> { // Unhide
                changeChatroomStatus(username, false)
                adapter.conversations.clear()
                adapter.conversations.addAll(ConversationAdapter.getConversationList())
                adapter.notifyDataSetChanged()
                return true
            }
        }
        return false
    }
}

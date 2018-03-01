package com.zeusro.wechatmagician.frontend.fragments

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.Toast
import com.zeusro.wechatmagician.Global.STATUS_FLAG_URI_ROUTER
import com.zeusro.wechatmagician.Global.WECHAT_PACKAGE_NAME
import com.zeusro.wechatmagician.R
import com.zeusro.wechatmagician.frontend.fragments.StatusFragment.Companion.requireHookStatus
import com.zeusro.wechatmagician.util.AlipayUtil
import kotlinx.android.synthetic.main.fragment_donate.*


class DonateFragment : Fragment() {

    private val alipayCode = "FKX07086PUFHES2KTRBP84"
    private val tenpayCode = "f2f0Wy61wbw1ftqwPa7YDD779T-iOitvjgiu"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_donate, container, false)

    override fun onStart() {
        super.onStart()

        // Hide Tenpay if the URI router is not hijacked.
        requireHookStatus(context!!, { status ->
            if (status[STATUS_FLAG_URI_ROUTER] != true) {
                donate_tenpay.visibility = GONE
            }
        })

        // Set onClick listeners for donation buttons.
        donate_alipay.setOnClickListener { view ->
            if (!AlipayUtil.hasInstalledAlipayClient(view.context)) {
                Toast.makeText(view.context, R.string.prompt_alipay_not_found, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(view.context, R.string.prompt_give_me_money, Toast.LENGTH_SHORT).show()
            AlipayUtil.startAlipayClient(view.context, alipayCode)
        }
        donate_tenpay.setOnClickListener { view ->
            val className = "$WECHAT_PACKAGE_NAME.plugin.base.stub.WXCustomSchemeEntryActivity"
            val componentName = ComponentName(WECHAT_PACKAGE_NAME, className)
            try {
                view.context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    component = componentName
                    data = Uri.parse("weixin://magician/donate/$tenpayCode")
                    flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
                })
                Toast.makeText(view.context, R.string.prompt_wait, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(view.context, t.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        fun newInstance(): DonateFragment = DonateFragment()
    }
}

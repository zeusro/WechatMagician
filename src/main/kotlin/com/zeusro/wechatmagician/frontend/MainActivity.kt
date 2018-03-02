package com.zeusro.wechatmagician.frontend

import android.content.Context
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.MenuItem
import android.widget.TextView
import com.zeusro.wechatmagician.Global.PREFERENCE_NAME_DEVELOPER
import com.zeusro.wechatmagician.Global.PREFERENCE_NAME_SETTINGS
import com.zeusro.wechatmagician.R
import com.zeusro.wechatmagician.frontend.fragments.DonateFragment
import com.zeusro.wechatmagician.frontend.fragments.PrefFragment
import com.zeusro.wechatmagician.frontend.fragments.StatusFragment
import com.zeusro.wechatmagician.frontend.fragments.SupportFragment
import com.zeusro.wechatmagician.util.LocaleUtil
import com.zeusro.wechatmagician.util.ViewUtil.openURL
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity(),
        NavigationView.OnNavigationItemSelectedListener {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtil.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)

        val headerView = nav_view.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.project_github_link).setOnClickListener {
            openURL(this, getString(R.string.view_about_project_github_url))
        }

        if (main_container != null && savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.main_container, StatusFragment.newInstance())
                    .commit()
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(Gravity.START)) {
            drawer_layout.closeDrawer(Gravity.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (main_container == null) {
            return false
        }

        val fragment: Fragment = when (item.itemId) {
            R.id.nav_status -> {
                StatusFragment.newInstance()
            }
            R.id.nav_settings -> {
                PrefFragment.newInstance(R.xml.pref_settings, PREFERENCE_NAME_SETTINGS)
            }
            R.id.nav_developer -> {
                PrefFragment.newInstance(R.xml.pref_developer, PREFERENCE_NAME_DEVELOPER)
            }
            R.id.nav_support -> {
                SupportFragment.newInstance()
            }
            R.id.nav_donate -> {
                DonateFragment.newInstance()
            }
            else ->
                throw Error("Unknown navigation item: ${item.itemId}")
        }
        supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, fragment)
                .commit()

        drawer_layout.closeDrawer(Gravity.START)
        return true
    }
}

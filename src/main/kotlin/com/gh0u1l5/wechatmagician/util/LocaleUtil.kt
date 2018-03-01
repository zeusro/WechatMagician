package com.zeusro.wechatmagician.util

import android.annotation.TargetApi
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Build
import com.zeusro.wechatmagician.Global.PREFERENCE_NAME_SETTINGS
import com.zeusro.wechatmagician.Global.SETTINGS_MODULE_LANGUAGE
import com.zeusro.wechatmagician.storage.LocalizedStrings
import java.util.*

/**
 * Created by devdeeds.com on 18/4/17.
 * by Jayakrishnan P.M
 */

object LocaleUtil {

    private fun Context.getProtectedSharedPreferences(name: String, mode: Int): SharedPreferences {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            getSharedPreferences(name, mode)
        } else {
            createDeviceProtectedStorageContext().getSharedPreferences(name, mode)
        }
    }

    fun getLanguage(context: Context, default: String = Locale.getDefault().language): String {
        val settings = context.getProtectedSharedPreferences(PREFERENCE_NAME_SETTINGS, MODE_PRIVATE)
        return settings.getString(SETTINGS_MODULE_LANGUAGE, default)
    }

    fun onAttach(context: Context, default: String = Locale.getDefault().language): Context {
        val language = getLanguage(context, default)
        return setLocale(context, language)
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        LocalizedStrings.language = language

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResources(context, language)
        } else {
            updateResourcesLegacy(context, language)
        }

    }

    private fun persist(context: Context, language: String) {
        val settings = context.getProtectedSharedPreferences(PREFERENCE_NAME_SETTINGS, MODE_PRIVATE)
        settings.edit()
                .putString(SETTINGS_MODULE_LANGUAGE, language)
                .apply()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    private fun updateResourcesLegacy(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = resources.configuration
        val displayMetrics = resources.displayMetrics
        resources.configuration.locale = locale
        resources.updateConfiguration(configuration, displayMetrics)

        return context
    }
}
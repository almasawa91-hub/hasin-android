package com.hasin.mobile.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.hasin.mobile.core.ThreatScoreEngine

class AppScannerRepository(private val context: Context) {

    fun getInstalledApps(): List<ThreatScoreEngine.AppThreatResult> {
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_PERMISSIONS)
        
        val results = mutableListOf<ThreatScoreEngine.AppThreatResult>()

        for (appInfo in installedPackages) {
            // تجاهل تطبيقات النظام الأساسية لتقليل الضوضاء في النسخة الأولى (اختياري)
            // if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            val appName = appInfo.loadLabel(packageManager).toString()
            
            try {
                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                val threatResult = ThreatScoreEngine.evaluate(packageInfo, appName)
                results.add(threatResult)
            } catch (e: PackageManager.NameNotFoundException) {
                // تجاهل الحزم التي لا يمكن قراءة معلوماتها
                continue
            }
        }

        // ترتيب النتائج حسب درجة الخطر (الأعلى خطراً أولاً)
        return results.sortedByDescending { it.score }
    }
}

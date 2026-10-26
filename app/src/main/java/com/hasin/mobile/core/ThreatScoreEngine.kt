package com.hasin.mobile.core

import android.content.pm.PackageInfo

object ThreatScoreEngine {
    
    // أذونات عالية الخطورة (تستحق 25 نقطة لكل منها)
    private val HIGH_RISK_PERMISSIONS = setOf(
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.READ_PHONE_STATE"
    )

    // أذونات متوسطة الخطورة (تستحق 10 نقاط لكل منها)
    private val MEDIUM_RISK_PERMISSIONS = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.WAKE_LOCK",
        "android.permission.RECEIVE_BOOT_COMPLETED"
    )

    data class AppThreatResult(
        val packageName: String,
        val appName: String,
        val score: Int, // 0 to 100
        val riskFactors: List<String>
    )

    fun evaluate(packageInfo: PackageInfo, appName: String): AppThreatResult {
        val permissions = packageInfo.requestedPermissions ?: emptyArray()
        var score = 0
        val riskFactors = mutableListOf<String>()

        for (perm in permissions) {
            when {
                HIGH_RISK_PERMISSIONS.contains(perm) -> {
                    score += 25
                    riskFactors.add(getPermissionName(perm))
                }
                MEDIUM_RISK_PERMISSIONS.contains(perm) -> {
                    score += 10
                }
            }
        }

        // سقف الدرجة هو 100
        score = score.coerceAtMost(100)

        // إذا لم يكن هناك أذونات خطيرة، نمنح درجة أساسية منخفضة
        if (score == 0 && permissions.isNotEmpty()) {
            score = 5
        }

        return AppThreatResult(
            packageName = packageInfo.packageName,
            appName = appName,
            score = score,
            riskFactors = riskFactors.distinct()
        )
    }

    private fun getPermissionName(perm: String): String {
        return perm.substringAfterLast(".").replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

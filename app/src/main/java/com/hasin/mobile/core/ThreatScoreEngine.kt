package com.hasin.mobile.core

import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.content.pm.PackageManager

object ThreatScoreEngine {

    private val HIGH_RISK_PERMISSIONS = setOf(
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION"
    )

    private val MEDIUM_RISK_PERMISSIONS = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.WAKE_LOCK",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.VIBRATE"
    )

    data class EvaluationResult(
        val score: Int,
        val highRiskPermissions: List<String>,
        val mediumRiskPermissions: List<String>,
        val totalPermissions: Int
    )

    fun evaluate(packageInfo: PackageInfo): EvaluationResult {
        val permissions = packageInfo.requestedPermissions ?: emptyArray()
        val highRisk = mutableListOf<String>()
        val mediumRisk = mutableListOf<String>()
        var score = 0

        for (perm in permissions) {
            when {
                perm in HIGH_RISK_PERMISSIONS -> {
                    score += 20
                    highRisk.add(shortName(perm))
                }
                perm in MEDIUM_RISK_PERMISSIONS -> {
                    score += 5
                    mediumRisk.add(shortName(perm))
                }
            }
        }

        return EvaluationResult(
            score = score.coerceAtMost(100),
            highRiskPermissions = highRisk.distinct(),
            mediumRiskPermissions = mediumRisk.distinct(),
            totalPermissions = permissions.size
        )
    }

    private fun shortName(perm: String): String =
        perm.substringAfterLast(".").replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
}

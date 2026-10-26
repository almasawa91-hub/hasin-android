package com.hasin.mobile.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.hasin.mobile.core.ThreatScoreEngine
import com.hasin.mobile.data.entity.NetworkConnectionEntity
import com.hasin.mobile.data.entity.ScannedAppEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class AppScannerRepository(
    private val context: Context,
    private val database: HasinDatabase
) {
    private val packageManager: PackageManager = context.packageManager
    private val scannedAppDao = database.scannedAppDao()
    private val networkDao = database.networkConnectionDao()

    fun getAllApps(): Flow<List<ScannedAppEntity>> = scannedAppDao.getAllAppsByRisk()
    fun getTotalCount(): Flow<Int> = scannedAppDao.getTotalCount()
    fun getAverageScore(): Flow<Double> = scannedAppDao.getAverageScore()
    fun getHighRiskCount(): Flow<Int> = scannedAppDao.getHighRiskCount()
    fun getMediumRiskCount(): Flow<Int> = scannedAppDao.getMediumRiskCount()
    fun getLowRiskCount(): Flow<Int> = scannedAppDao.getLowRiskCount()
    fun getWorsenedCount(): Flow<Int> = scannedAppDao.getWorsenedCount()
    
    // الإضافة الجديدة المطلوبة
    fun getRecentConnections(): Flow<List<NetworkConnectionEntity>> = networkDao.getRecentConnections()

    suspend fun scanAllApps() {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_PERMISSIONS)

        for (appInfo in installedApps) {
            try {
                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                val evaluation = ThreatScoreEngine.evaluate(packageInfo)
                val appName = appInfo.loadLabel(packageManager).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val previous = scannedAppDao.getApp(appInfo.packageName)
                val previousScore = previous?.threatScore
                val trend = when {
                    previousScore == null -> 0
                    evaluation.score > previousScore -> 1
                    evaluation.score < previousScore -> -1
                    else -> 0
                }

                val entity = ScannedAppEntity(
                    packageName = appInfo.packageName,
                    appName = appName,
                    versionName = packageInfo.versionName ?: "unknown",
                    versionCode = packageInfo.longVersionCode,
                    isSystemApp = isSystem,
                    installTime = packageInfo.firstInstallTime,
                    updateTime = packageInfo.lastUpdateTime,
                    threatScore = evaluation.score,
                    highRiskPermissions = evaluation.highRiskPermissions.toJson(),
                    mediumRiskPermissions = evaluation.mediumRiskPermissions.toJson(),
                    totalPermissions = evaluation.totalPermissions,
                    lastScannedAt = System.currentTimeMillis(),
                    previousScore = previousScore,
                    scoreTrend = trend
                )
                scannedAppDao.upsert(entity)
            } catch (e: Exception) {
                continue
            }
        }
    }

    private fun List<String>.toJson(): String {
        val arr = JSONArray()
        forEach { arr.put(it) }
        return arr.toString()
    }
}

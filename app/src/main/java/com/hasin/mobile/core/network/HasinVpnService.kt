package com.hasin.mobile.core.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.hasin.mobile.MainActivity
import com.hasin.mobile.data.HasinDatabase
import com.hasin.mobile.data.entity.NetworkConnectionEntity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class HasinVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "hasin_vpn_channel"
        
        fun startService(context: Context) {
            val intent = Intent(context, HasinVpnService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, HasinVpnService::class.java).apply { action = "STOP" }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startForeground(1, createNotification("جاري مراقبة حزم الشبكة محلياً..."))
        
        if (!isRunning) {
            startVpn()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "حصين مراقبة الشبكة", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("حصين")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun startVpn() {
        val builder = Builder()
            .setSession("Hasin Monitor")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .setBlocking(true)

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            stopSelf()
            return
        }

        isRunning = true
        scope.launch {
            processPackets()
        }
    }

    private suspend fun processPackets() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val packet = ByteArray(32767)
        val db = HasinDatabase.getInstance(applicationContext)
        val dao = db.networkConnectionDao()

        while (isRunning) {
            try {
                val length = inputStream.read(packet)
                if (length > 0) {
                    analyzeAndLog(packet, length, dao)
                }
            } catch (e: Exception) {
                if (isRunning) delay(100)
            }
        }
    }

    private suspend fun analyzeAndLog(packet: ByteArray, length: Int, dao: com.hasin.mobile.data.dao.NetworkConnectionDao) {
        if (length < 20) return
        
        val buffer = ByteBuffer.wrap(packet, 0, length)
        val versionAndHeaderLength = buffer.get().toInt()
        val headerLength = (versionAndHeaderLength and 0x0F) * 4
        
        if (headerLength < 20 || length < headerLength + 4) return

        val protocol = buffer.get(9).toInt() // 6 = TCP, 17 = UDP
        
        val destIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        
        val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or (packet[headerLength + 3].toInt() and 0xFF)

        val isSuspicious = isSuspiciousPort(destPort)
        
        if (isSuspicious) {
            val entity = NetworkConnectionEntity(
                destIp = destIp,
                destPort = destPort,
                protocol = if (protocol == 6) "TCP" else "UDP",
                timestamp = System.currentTimeMillis(),
                isSuspicious = true,
                reason = "منفذ غير قياسي أو مشبوه"
            )
            dao.insertConnection(entity)
        }
    }

    private fun isSuspiciousPort(port: Int): Boolean {
        // أمثلة على منافذ مرتبطة بأدوات اختراق أو اتصالات غير موثوقة
        return port in listOf(4444, 5555, 6666, 31337, 12345, 8080)
    }

    private fun stopVpn() {
        isRunning = false
        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }
}

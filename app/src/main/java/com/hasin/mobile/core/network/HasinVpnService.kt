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
import java.io.FileOutputStream
import kotlinx.coroutines.channels.Channel

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

        startForeground(1, createNotification("جاري حماية الشبكة بأداء عالي..."))
        
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
            processAndForwardPackets()
        }
    }

    private suspend fun processAndForwardPackets() {
        val fileDescriptor = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fileDescriptor)
        val outputStream = FileOutputStream(fileDescriptor)
        val packet = ByteArray(32767)
        
        val db = HasinDatabase.getInstance(applicationContext)
        val dao = db.networkConnectionDao()
        
        // قناة غير حاجزة لإرسال التنبيهات لمعالجتها في الخلفية
        val suspiciousChannel = Channel<NetworkConnectionEntity>(Channel.CONFLATED)
        
        // مهمة خلفية منفصلة لحفظ البيانات في قاعدة البيانات دون إبطاء الشبكة
        scope.launch {
            for (entity in suspiciousChannel) {
                try {
                    dao.insertConnection(entity)
                } catch (e: Exception) {
                    // تجاهل أخطاء قاعدة البيانات المؤقتة للحفاظ على استقرار الخدمة
                }
            }
        }

        while (isRunning) {
            try {
                val length = inputStream.read(packet)
                if (length > 0) {
                    // 1. تحليل سريع جداً (بدون عمليات إدخال/إخراج حاجزة)
                    val threat = analyzePacketFast(packet, length)
                    
                    if (threat != null) {
                        // إرسال غير حاجز للقناة
                        suspiciousChannel.trySend(threat)
                    }
                    
                    // 2. إعادة توجيه الحزمة فوراً (بدون flush يدوي مدمر للأداء)
                    outputStream.write(packet, 0, length)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    delay(50) // راحة قصيرة في حال حدوث خطأ مؤقت في الشبكة
                }
            }
        }
    }

    private fun analyzePacketFast(packet: ByteArray, length: Int): NetworkConnectionEntity? {
        // التحقق من أن الحزمة طويلة بما يكفي لتكون حزمة IP (20 بايت كحد أدنى)
        if (length < 20) return null
        
        val versionAndHeaderLength = packet[0].toInt()
        val headerLength = (versionAndHeaderLength and 0x0F) * 4
        
        if (headerLength < 20 || length < headerLength + 4) return null

        val protocol = packet[9].toInt() // 6 = TCP, 17 = UDP
        
        // استخراج عنوان IP الوجهة (بايتات 16 إلى 19)
        val destIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        
        // استخراج منفذ الوجهة (يأتي بعد رأس IP مباشرة)
        val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or (packet[headerLength + 3].toInt() and 0xFF)

        // منطق الكشف السريع عن التهديدات
        val suspiciousPorts = listOf(4444, 5555, 6666, 31337, 12345, 8080, 8888)
        if (destPort in suspiciousPorts) {
            return NetworkConnectionEntity(
                destIp = destIp,
                destPort = destPort,
                protocol = if (protocol == 6) "TCP" else "UDP",
                timestamp = System.currentTimeMillis(),
                isSuspicious = true,
                reason = "منفذ شبكي مشبوه"
            )
        }

        return null
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

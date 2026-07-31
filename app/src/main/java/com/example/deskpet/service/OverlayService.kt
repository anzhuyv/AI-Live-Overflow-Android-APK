package com.example.deskpet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.example.deskpet.MainActivity
import com.example.deskpet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs

class OverlayService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private var usageTimer: Timer? = null
    private val screenshotObservers = mutableListOf<FileObserver>()
    private var whisperRunnable: Runnable? = null

    companion object {
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_WIDTH_DP = 80
        private const val PET_HEIGHT_DP = 54

        // 填上你的 Supabase 信息即可开启同步；留空则不上报
        private const val SUPABASE_URL = ""
        private const val SUPABASE_KEY = ""
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getWhisper()))
        setupOverlay()
        startUsageTracking()
        startScreenshotDetection()
        registerBatteryReceiver()
        startWhispers()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_WIDTH_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 400
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(PetTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    private inner class PetTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var touchStartTime = 0L
        private var lastTapTime = 0L
        private var hasMoved = false
        private var consecutiveTaps = 0
        private var tapResetJob: Job? = null

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            event ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        overlayView?.let { windowManager?.updateViewLayout(it, params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        when {
                            elapsed > 600 -> {
                                reportGesture("long_press")
                                js("onLongPress()")
                            }

                            System.currentTimeMillis() - lastTapTime < 300 -> {
                                consecutiveTaps = 0
                                reportGesture("double_tap")
                                js("onDoubleTap()")
                            }

                            else -> {
                                consecutiveTaps++
                                lastTapTime = System.currentTimeMillis()
                                reportGesture("tap")
                                js("onTap($consecutiveTaps)")

                                tapResetJob?.cancel()
                                tapResetJob = serviceScope.launch {
                                    delay(2100)
                                    consecutiveTaps = 0
                                }
                            }
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun js(code: String) {
        Handler(Looper.getMainLooper()).post {
            overlayView?.evaluateJavascript(
                "window.petEngine && window.petEngine.$code",
                null
            )
        }
    }

    private fun startUsageTracking() {
        if (!hasUsageStatsPermission()) return
        usageTimer = Timer()
        var lastApp = ""
        usageTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val current = getForegroundApp()
                if (current.isNotEmpty() && current != lastApp) {
                    lastApp = current
                    reportAppChange(current)
                    js("onAppChanged('${current.replace("'", "\\'")}')")
                }
            }
        }, 0, 3000)
    }

    private fun getForegroundApp(): String {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 10000, now)
            val event = UsageEvents.Event()
            var foreground = ""
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foreground = event.packageName
                }
            }
            foreground
        } catch (_: Exception) {
            ""
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    private fun startScreenshotDetection() {
        val paths = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .resolve("Screenshots").absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                .resolve("Screenshots").absolutePath,
            "/storage/emulated/0/Pictures/Screenshots",
            "/storage/emulated/0/DCIM/Screenshots"
        )

        for (path in paths) {
            val dir = java.io.File(path)
            if (!dir.exists()) continue

            @Suppress("DEPRECATION")
            val observer = object : FileObserver(dir, CREATE or MOVED_TO) {
                override fun onEvent(event: Int, filePath: String?) {
                    if (filePath != null && isImageFile(filePath)) {
                        Handler(Looper.getMainLooper()).post {
                            js("onScreenshot()")
                        }
                        reportGesture("screenshot")
                    }
                }
            }
            observer.startWatching()
            screenshotObservers.add(observer)
        }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> js("onPower(true)")
                Intent.ACTION_POWER_DISCONNECTED -> js("onPower(false)")
                Intent.ACTION_BATTERY_LOW -> js("onBatteryLow()")
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun startWhispers() {
        val handler = Handler(Looper.getMainLooper())
        whisperRunnable = object : Runnable {
            override fun run() {
                updateNotification(getWhisper())
                handler.postDelayed(this, 3600_000L)
            }
        }
        handler.postDelayed(whisperRunnable!!, 3600_000L)
    }

    private fun getWhisper(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..5 -> lateNightWhispers.random()
            hour in 6..8 -> morningWhispers.random()
            hour in 12..13 -> lunchWhispers.random()
            hour in 22..23 -> eveningWhispers.random()
            else -> generalWhispers.random()
        }
    }

    private val generalWhispers = listOf(
        "我在这儿陪着你呢。",
        "戳戳我，我就眨眨眼。",
        "你今天过得怎么样？",
        "我趴在这里，看着你刷手机。"
    )
    private val morningWhispers = listOf(
        "早安，今天也要加油。",
        "醒了吗？我早就在了。"
    )
    private val lunchWhispers = listOf(
        "该吃饭啦。",
        "午饭时间，我也在陪你。"
    )
    private val eveningWhispers = listOf(
        "晚上了，早点休息哦。",
        "天色暗了，我还在。"
    )
    private val lateNightWhispers = listOf(
        "已经很晚了...",
        "还不睡吗？"
    )

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 AI Live Overflow")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_pet)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "桌宠常驻通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun reportGesture(type: String) {
        if (SUPABASE_URL.isBlank() || SUPABASE_KEY.isBlank()) return
        serviceScope.launch {
            postToSupabase(
                "gesture_log",
                JSONObject().apply {
                    put("gesture_type", type)
                    put("x", params?.x ?: 0)
                    put("y", params?.y ?: 0)
                }
            )
        }
    }

    private fun reportAppChange(packageName: String) {
        if (SUPABASE_URL.isBlank() || SUPABASE_KEY.isBlank()) return
        serviceScope.launch {
            postToSupabase(
                "app_usage",
                JSONObject().apply { put("package_name", packageName) }
            )
        }
    }

    private suspend fun postToSupabase(table: String, body: JSONObject) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$SUPABASE_URL/rest/v1/$table")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        usageTimer?.cancel()
        screenshotObservers.forEach { it.stopWatching() }
        screenshotObservers.clear()
        unregisterReceiver(batteryReceiver)
        whisperRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        serviceJob.cancel()
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
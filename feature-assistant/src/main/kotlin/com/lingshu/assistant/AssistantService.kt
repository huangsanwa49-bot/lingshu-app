package com.lingshu.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lingshu.core.AuthState
import com.lingshu.core.HubException
import com.lingshu.core.Notice
import com.lingshu.core.Lingshu
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 小助理的前台服务：维持悬浮球、长轮询 lingshu 的通知并播报、按时段问候。
 * App 在前台时悬浮球隐藏；退到后台才出现。
 */
class AssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: AssistantPrefs
    private lateinit var speaker: Speaker
    private var bubble: BubbleController? = null
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Assistant.prefs(this)
        speaker = Speaker(this)
        Assistant.speaker?.shutdown() // 界面里临时建的那个，换成服务自己的
        Assistant.speaker = speaker
        createChannels()
        ServiceCompat.startForeground(
            this,
            ONGOING_ID,
            ongoingNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        scope.launch {
            combine(Lingshu.foreground, Lingshu.auth, prefs.bubble) { fg, auth, enabled ->
                Triple(fg, auth is AuthState.SignedIn, enabled)
            }.collect { (fg, signedIn, enabled) ->
                if (!signedIn) {
                    stopSelf()
                    return@collect
                }
                if (!fg && enabled && Settings.canDrawOverlays(this@AssistantService)) {
                    showBubble()
                    maybeAutoBrief()
                } else {
                    bubble?.hide()
                }
            }
        }
        pollJob = scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 常驻通知上的“停止”：等同于在设置里关掉悬浮小助理
            prefs.setBubble(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        bubble?.hide()
        speaker.shutdown()
        if (Assistant.speaker === speaker) Assistant.speaker = null
        super.onDestroy()
    }

    private fun showBubble() {
        val b = bubble ?: BubbleController(
            this,
            prefs,
            onTap = { Assistant.openQuickChat(this) },
            onLongPress = { scope.launch { brief(force = true) } },
        ).also { bubble = it }
        b.show()
    }

    // ---- 问候播报 ----

    private fun slotNow(): String {
        val now = LocalDateTime.now()
        val part = when (now.hour) {
            in 5..10 -> "morning"
            in 11..13 -> "noon"
            in 14..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
        return "${now.toLocalDate()}-$part"
    }

    private suspend fun maybeAutoBrief() {
        if (!prefs.autoBrief.value) return
        val slot = slotNow()
        if (slot.endsWith("night") || prefs.lastBriefSlot == slot) return // 夜里不主动出声
        prefs.lastBriefSlot = slot
        brief(force = false)
    }

    private suspend fun brief(force: Boolean) {
        try {
            val b = Lingshu.api.brief()
            bubble?.unread = b.unread
            val line = buildString {
                append(b.greeting).append("。")
                b.weather?.let { append("天气").append(it).append("。") }
                append(b.care)
            }
            bubble?.say(line, 9000)
            if (prefs.speak.value) speaker.say(b.speech)
        } catch (e: HubException) {
            if (force) bubble?.say(e.message ?: "连不上 lingshu", 5000)
        }
    }

    // ---- 通知 ----

    private suspend fun pollLoop() {
        var backoff = 5_000L
        while (scope.isActive) {
            if (Lingshu.auth.value !is AuthState.SignedIn) {
                delay(5_000)
                continue
            }
            try {
                if (prefs.lastNoticeId < 0) {
                    // 首次同步：跳过以前的通知，不一次性播报一堆旧消息
                    var after = 0L
                    while (true) {
                        val page = Lingshu.api.notifications(after, 0)
                        after = page.latestId
                        if (page.items.size < 50) break
                    }
                    prefs.lastNoticeId = after
                }
                val page = Lingshu.api.notifications(prefs.lastNoticeId, WAIT_SECONDS)
                page.items.forEach(::announce)
                if (page.items.isNotEmpty()) {
                    prefs.lastNoticeId = page.latestId
                    bubble?.let { it.unread += page.items.size }
                }
                backoff = 5_000L
            } catch (e: HubException) {
                // 不在家里的 Wi-Fi、lingshu 重启等：逐步拉长重试间隔，最长 5 分钟，省电
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(300_000L)
            }
        }
    }

    private fun announce(n: Notice) {
        val text = if (n.body.isNotBlank() && n.body != n.title) "${n.title}：${n.body}" else n.title
        bubble?.say(text, 8000)
        if (prefs.speak.value) speaker.say(text, queue = true)
        postSystemNotification(n)
    }

    private fun postSystemNotification(n: Notice) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_NOTICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title)
            .setContentText(n.body.ifBlank { n.title })
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body.ifBlank { n.title }))
            .setPriority(if (n.kind == "alert" || n.kind == "approval") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            // 锁屏上只显示“lingshu 有新通知”，内容解锁后才看得到
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, CHANNEL_NOTICE)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("lingshu 有新通知")
                    .build(),
            )
            .setContentIntent(launchIntent())
            .setAutoCancel(true)
            .build()
        nm.notify(NOTICE_BASE_ID + (n.id % 1000).toInt(), notif)
    }

    // ---- 系统通知栏 ----

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "小助理在后台", NotificationManager.IMPORTANCE_MIN).apply {
                description = "悬浮小助理运行时的常驻提示"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTICE, "lingshu 通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "任务完成、待确认的操作、家里的提醒"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ongoingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("lingshu 小助理")
            .setContentText("在后台陪着你，有消息会提醒")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(launchIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private companion object {
        const val ACTION_STOP = "com.lingshu.assistant.STOP"
        const val WAIT_SECONDS = 25
        const val ONGOING_ID = 1
        const val NOTICE_BASE_ID = 1000
        const val CHANNEL_ONGOING = "assistant_ongoing"
        const val CHANNEL_NOTICE = "lingshu_notice"
    }
}

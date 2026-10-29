package com.example.yanji.bridge

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.yanji.liveactivity.FocusNotificationSpecs
import com.example.yanji.liveactivity.StandardNotificationController
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Ask in the context of starting a timer, before the foreground service is launched. */
internal suspend fun prepareTimerNotifications(context: ReactApplicationContext) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        val activity = context.currentActivity as? PermissionAwareActivity
        if (activity != null) suspendCancellableCoroutine<Unit> { continuation ->
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7501,
                PermissionListener { code, _, _ ->
                    if (code != 7501) false else {
                        if (continuation.isActive) continuation.resume(Unit)
                        true
                    }
                })
        }
    }
    StandardNotificationController(context).createChannel()
    val manager = context.getSystemService(NotificationManager::class.java)
    val channelsEnabled = Build.VERSION.SDK_INT < 26 || listOf(
        FocusNotificationSpecs.CHANNEL_ID, FocusNotificationSpecs.COMPLETION_CHANNEL_ID
    ).all { manager?.getNotificationChannel(it)?.importance != NotificationManager.IMPORTANCE_NONE }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled() || !channelsEnabled) {
        Toast.makeText(context, "通知未开启，后台计时和完成提醒可能不可见，可在系统通知设置中开启", Toast.LENGTH_LONG).show()
    }
}

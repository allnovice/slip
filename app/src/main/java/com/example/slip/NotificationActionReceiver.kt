package com.example.slip

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val sessionId = intent.getStringExtra("session_id") ?: return
        val notificationId = intent.getIntExtra("notification_id", -1)

        val repository = SleepDataRepository.getInstance(context.applicationContext)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            "ACTION_CONFIRM_NAP" -> {
                Log.d("NotifyReceiver", "User confirmed NAP for $sessionId")
                // No database change needed as it's already tagged as NAP
                notificationManager.cancel(notificationId)
            }
            "ACTION_MARK_IDLE" -> {
                Log.d("NotifyReceiver", "User marked $sessionId as IDLE")
                CoroutineScope(Dispatchers.IO).launch {
                    repository.labelSessionById(sessionId, SleepSession.CATEGORY_IDLE)
                }
                notificationManager.cancel(notificationId)
            }
        }
    }
}

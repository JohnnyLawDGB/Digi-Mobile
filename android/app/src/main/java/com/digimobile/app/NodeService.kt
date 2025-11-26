package com.digimobile.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.digimobile.node.DigiMobileNodeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NodeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodeController = DigiMobileNodeController()
    private lateinit var bootstrapper: NodeBootstrapper

    override fun onCreate() {
        super.onCreate()
        bootstrapper = NodeBootstrapper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting DigiByte node..."))
        scope.launch {
            val paths = bootstrapper.ensureBootstrap()
            try {
                nodeController.startNode(this@NodeService, paths.configFile.absolutePath, paths.dataDir.absolutePath)
                updateNotification("Digi-Mobile node running")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start node: ${e.message}", e)
                updateNotification("Node error: ${e.message}")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val stopJob = scope.launch {
            try {
                nodeController.stopNode()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop node: ${e.message}", e)
            }
        }

        stopJob.invokeOnCompletion { scope.coroutineContext.cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        createChannel()
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Digi-Mobile")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Digi-Mobile Node",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Runs the DigiByte node in the background"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "NodeService"
        private const val CHANNEL_ID = "digimobile-node"
        private const val NOTIFICATION_ID = 1001
    }
}

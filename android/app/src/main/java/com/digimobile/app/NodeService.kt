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
import com.digimobile.node.NodeManager
import com.digimobile.node.NodeManagerProvider
import com.digimobile.node.NodeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NodeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var nodeManager: NodeManager
    private var stateJob: Job? = null
    private var isStopping = false

    override fun onCreate() {
        super.onCreate()
        nodeManager = NodeManagerProvider.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            nodeManager.appendLog("NodeService received stop action")
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Stopping Digi-Mobile node…")
            )
            stateJob?.cancel()
            isStopping = true
            scope.launch {
                nodeManager.stopNode()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(nodeManager.nodeState.value.toNotificationText())
        )
        nodeManager.appendLog("NodeService started")
        startStateUpdates()
        nodeManager.startNode().invokeOnCompletion { throwable ->
            throwable?.let {
                nodeManager.appendLog("Node start failed: ${it.message}")
                Log.e(TAG, "Failed to start node: ${it.message}", it)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        nodeManager.appendLog("NodeService stopping")
        stateJob?.cancel()
        if (!isStopping) {
            scope.launch {
                nodeManager.stopNode()
                scope.coroutineContext.cancel()
            }
        } else {
            scope.coroutineContext.cancel()
        }
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

    private fun startStateUpdates() {
        if (stateJob != null) return
        stateJob = scope.launch {
            nodeManager.nodeState.collect { state ->
                updateNotification(state.toNotificationText())
            }
        }
    }

    private fun NodeState.toNotificationText(): String = when (this) {
        NodeState.Idle -> "Digi-Mobile node idle"
        NodeState.PreparingEnvironment -> "Preparing Digi-Mobile node environment..."
        is NodeState.DownloadingBinaries -> "Downloading binaries (${this.progress}%)..."
        NodeState.VerifyingBinaries -> "Verifying Digi-Mobile binaries..."
        NodeState.WritingConfig -> "Writing node configuration..."
        NodeState.StartingDaemon -> "Starting DigiByte daemon..."
        NodeState.ConnectingToPeers -> "Connecting to DigiByte peers..."
        is NodeState.Syncing -> {
            val progressText = this.progress?.let { "$it%" } ?: "progress unknown"
            val heightText = if (this.currentHeight != null && this.targetHeight != null) {
                " height ${this.currentHeight}/${this.targetHeight}"
            } else {
                ""
            }
            "Syncing ($progressText)$heightText"
        }
        NodeState.Ready -> "Digi-Mobile node running"
        is NodeState.Error -> "Node error: ${this.message}"
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
        const val ACTION_STOP = "com.digimobile.action.STOP"
    }
}

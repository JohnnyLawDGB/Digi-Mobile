package com.digimobile.node

import android.content.Context
import com.digimobile.app.NodeBootstrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object NodeManagerProvider {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var instance: NodeManager? = null

    fun get(context: Context): NodeManager {
        return instance ?: synchronized(this) {
            instance ?: createManager(context.applicationContext).also { instance = it }
        }
    }

    private fun createManager(context: Context): NodeManager {
        val bootstrapper = NodeBootstrapper(context)
        val controller = DigiMobileNodeController()
        return NodeManager(context, bootstrapper, controller, managerScope)
    }
}


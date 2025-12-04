package com.digimobile.node

import com.digimobile.app.NodeBootstrapper

object NodeEnvironment {

    data class Environment(
        val paths: NodeBootstrapper.NodePaths,
        val rpcCredentials: RpcCredentials
    )

    @Volatile
    private var environment: Environment? = null

    val paths: NodeBootstrapper.NodePaths?
        get() = environment?.paths

    val rpcCredentials: RpcCredentials?
        get() = environment?.rpcCredentials

    fun update(paths: NodeBootstrapper.NodePaths, rpcCredentials: RpcCredentials) {
        environment = Environment(paths, rpcCredentials)
    }
}

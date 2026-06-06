package com.staploy.server

import com.staploy.server.commons.service.Argument
import com.staploy.server.commons.service.Service
import com.staploy.server.commons.modules.configureHTTP
import com.staploy.server.commons.modules.configureRouting
import com.staploy.server.commons.modules.configureSerialization
import com.staploy.server.commons.modules.configureSockets
import com.staploy.server.commons.service.Helpers

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.network.port

import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import kotlinx.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val argObj: Argument = Argument.buildFrom(args.toList())
    Service.configureServiceInstance(argObj)

    val mtlsContext: SslContext? = if (argObj.useWorkerMtls) {
        try {
            SslContextBuilder.forServer(File(argObj.mTlsChain), File(argObj.mTlsKey))
                .trustManager(File(argObj.mTlsCaCert))
                .clientAuth(ClientAuth.REQUIRE)
                .build()
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
    } else {
        null
    }

    val server = embeddedServer(Netty, applicationEnvironment { }, configure = {
        connectors.add(EngineConnectorBuilder().apply {
            host = argObj.host
            port = argObj.adminPort
        })

        connectors.add(EngineConnectorBuilder().apply {
            host = argObj.host
            port = argObj.workerPort
        })

        channelPipelineConfig = {
            val localAddress = channel().localAddress()
            val localPort = localAddress.port

            if (argObj.useWorkerMtls && localPort == argObj.workerPort) {
                val sslEngine = mtlsContext?.newEngine(channel().alloc())
                channel().pipeline().addFirst("ssl", SslHandler(sslEngine))
            }
        }
    }, Application::module).start(wait = false)

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(1, 5, TimeUnit.SECONDS)
        Helpers.invokeOnDead()
    })

    Helpers.invokeOnLoad()
    Thread.currentThread().join()
}

fun Application.module() {
    configureSockets()
    configureSerialization()
    configureHTTP()
    configureRouting()
}

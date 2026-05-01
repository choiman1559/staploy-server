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
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val argObj: Argument = Argument.buildFrom(args.toList())
    Service.configureServiceInstance(argObj)

    val server = embeddedServer(Netty, port = argObj.port, host = argObj.host, module = Application::module)
        .start(wait = true)
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(1, 5, TimeUnit.SECONDS)
        Helpers.invokeOnDead()
    })
    Thread.currentThread().join()
}

fun Application.module() {
    configureSockets()
    configureSerialization()
    configureHTTP()
    configureRouting()
}

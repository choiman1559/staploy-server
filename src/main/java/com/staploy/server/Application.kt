package com.staploy.server

import com.staploy.server.modules.configureHTTP
import com.staploy.server.modules.configureRouting
import com.staploy.server.modules.configureSerialization
import com.staploy.server.modules.configureSockets
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSockets()
    configureSerialization()
    configureHTTP()
    configureRouting()
}

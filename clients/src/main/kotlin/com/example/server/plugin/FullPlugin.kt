package com.example.server.plugin

import com.example.server.MainController
import com.example.server.NodeRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class FullPlugin : WebServerPluginRegistry {

    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::MainController))

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.

    override val staticServeDirs = mapOf(
    // This will serve the exampleWeb directory in resources to /web/example
    "example" to javaClass.classLoader.getResource("exampleWeb").toExternalForm()
    )*/
}
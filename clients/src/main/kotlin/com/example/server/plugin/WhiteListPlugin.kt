package com.example.server.plugin

import net.corda.core.serialization.SerializationWhitelist
import java.util.*

class WhiteListPlugin : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(Date::class.java)
}
package com.example.POJO

import net.corda.core.serialization.CordaSerializable
import java.time.Instant
import java.util.*

@CordaSerializable
data class WasteRequestPojo(
        val cliente: String = "",
        val fornitore: String = "",
        val externalId: String = "",
        val codCliente: String = "",
        val codFornitore: String = "",
        val requestDate: Instant = Instant.now(),
        val wasteType: String = "",
        val wasteWeight: Double = 0.0,
        val wasteDesc: String = "",
        val wasteDescAmm: String = "",
        val wasteGps: String = "",
        val status: String = "",
        val idProposal: String = ""
)
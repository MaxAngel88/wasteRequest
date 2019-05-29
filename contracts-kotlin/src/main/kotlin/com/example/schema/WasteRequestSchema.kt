package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for IOUState.
 */
object WasteRequestSchema

/**
 * An IOUState schema.
 */
object WasteRequestSchemaV1 : MappedSchema(
        schemaFamily = WasteRequestSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentWasteRequest::class.java)) {
    @Entity
    @Table(name = "wasteRequest_states")
    class PersistentWasteRequest(

            @Column(name = "cliente")
            var clienteName: String,

            @Column(name = "fornitore")
            var fornitoreName: String,

            @Column(name = "syndial")
            var syndialName: String,

            @Column(name = "codCliente")
            var codCliente: String,

            @Column(name = "codFornitore")
            var codFornitore: String,

            @Column(name = "requestDate")
            var requestDate: Instant,

            @Column(name = "wasteType")
            var wasteType: String,

            @Column(name = "wasteWeight")
            var wasteWeight: Double,

            @Column(name = "wasteDesc")
            var wasteDesc: String,

            @Column(name = "wasteDescAmm")
            var wasteDescAmm: String,

            @Column(name = "wasteGps")
            var wasteGps: String,

            @Column(name = "status")
            var status: String,

            @Column(name = "idProposal")
            var idProposal: String,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        // constructor(): this("", "", 0, UUID.randomUUID())
        constructor() : this("","", "", "", "", Instant.now(), "", 0.0, "","","", "", "", UUID.randomUUID())

    }
}
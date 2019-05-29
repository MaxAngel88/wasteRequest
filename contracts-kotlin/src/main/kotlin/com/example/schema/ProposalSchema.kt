package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object ProposalSchema

object ProposalSchemaV1 : MappedSchema(
        schemaFamily = ProposalSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentProposal::class.java)) {

    @Entity
    @Table(name = "proposal_states")
    class PersistentProposal(

            @Column(name = "cliente")
            var cliente: String,

            @Column(name = "fornitore")
            var fornitore: String,

            @Column(name = "syndial")
            var syndial: String,

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

            @Column(name = "validity")
            var validity: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        //constructor(): this("", "", "", , UUID.randomUUID())
        constructor() : this("", "", "", "", "", Instant.now(),"", 0.0, "", "", "", "", Instant.now(), UUID.randomUUID())
    }
}
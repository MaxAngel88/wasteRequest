package com.example.state

import com.example.flow.ProposalFlow
import com.example.schema.ProposalSchemaV1
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant
import java.util.*

data class ProposalState (
        val cliente: Party,
        val fornitore: Party,
        val syndial: Party,

        val codCliente: String,
        val codFornitore: String,
        val requestDate: Instant,
        val wasteType: String,
        val wasteWeight: Double,
        val wasteDesc: String,
        val wasteDescAmm: String,
        val wasteGps: String,
        val status: String,
        val validity: Instant,

        override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState, SchedulableState {

    override val participants: List<AbstractParty> get() = listOf(cliente, fornitore, syndial)

    // TODO trovare un modo per importare proposalFlow

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return ScheduledActivity(flowLogicRefFactory.create(ProposalFlow.EndProposal::class.java, thisStateRef), validity)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState{
        return when (schema) {
            is ProposalSchemaV1 -> ProposalSchemaV1.PersistentProposal(
                    this.cliente.name.toString(),
                    this.fornitore.name.toString(),
                    this.syndial.name.toString(),
                    this.codCliente,
                    this.codFornitore,
                    this.requestDate,
                    this.wasteType,
                    this.wasteWeight,
                    this.wasteDesc,
                    this.wasteDescAmm,
                    this.wasteGps,
                    this.status,
                    this.validity,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ProposalSchemaV1)
}
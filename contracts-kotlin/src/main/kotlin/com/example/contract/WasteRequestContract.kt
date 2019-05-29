package com.example.contract

import com.example.state.ProposalState
import com.example.state.WasteRequestState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.*


class WasteRequestContract : Contract {
    companion object {
        @JvmStatic
        val WASTE_REQUEST_CONTRACT_ID = "com.example.contract.WasteRequestContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands){
            val setOfSigners = command.signers.toSet()
            when (command.value) {
                is Commands.Create -> verifyCreate(tx, setOfSigners)
                is Commands.Issue -> verifyIssue(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Issue : Commands
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs should be consumed when creating a transaction." using (tx.inputStates.isEmpty())
        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val wasteRequest = tx.outputsOfType<WasteRequestState>().single()
        "cliente and fornitore cannot be the same" using (wasteRequest.cliente != wasteRequest.fornitore)
        "date cannot be in the future" using (wasteRequest.requestDate < Instant.now())
        "wasteType cannot be empty" using (wasteRequest.wasteType.isNotEmpty())
        "wasteWeight must be grather than 0" using (wasteRequest.wasteWeight > 0.0)
        "wasteGps cannot be empty" using (wasteRequest.wasteGps.isNotEmpty())
        "wasteRequest status must be 'ongoing' or 'completed'" using (wasteRequest.status.equals("ongoing", ignoreCase = true) || wasteRequest.status.equals("completed", ignoreCase = true))

        "All of the participants must be signers." using (signers.containsAll(wasteRequest.participants.map { it.owningKey }))
    }

    private fun verifyIssue(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //proposal
        "there must be only one input" using (tx.inputStates.size == 1)
        val proposal = tx.inputsOfType<ProposalState>().single()

        //transaction
        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val wasteRequest = tx.outputsOfType<WasteRequestState>().single()
        "cliente and fornitore cannot be the same" using (wasteRequest.cliente != wasteRequest.fornitore)
        "wasteType cannot be empty" using (wasteRequest.wasteType.isNotEmpty())
        "wasteWeight must be grather than 0" using (wasteRequest.wasteWeight > 0.0)
        "wasteGps cannot be empty" using (wasteRequest.wasteGps.isNotEmpty())
        "wasteRequest status must be 'running' or 'completed'" using (wasteRequest.status.equals("running", ignoreCase = true) || wasteRequest.status.equals("completed", ignoreCase = true))

        "All of the participants must be signers." using (signers.containsAll(wasteRequest.participants.map { it.owningKey }))

        //proposal and transaction
        "proposal's cliente must be the wasteRequest's cliente" using (proposal.cliente == wasteRequest.cliente)
        "proposal's fornitore must be the wasteRequest's fornitore" using (proposal.fornitore == wasteRequest.fornitore)
        ""+proposal.requestDate+" is not valid" using (wasteRequest.requestDate < proposal.validity)
        "wasteType must be equal" using (proposal.wasteType == wasteRequest.wasteType)
        "wasteWeight must be equal" using (proposal.wasteWeight == wasteRequest.wasteWeight)
        "wasteGps must be equal" using (proposal.wasteGps == wasteRequest.wasteGps)
        "proposal status cannot be 'rejected'" using (!proposal.status.equals("rejected", ignoreCase = true))
        "linearId must be idProposal" using (proposal.linearId.id.toString() == wasteRequest.idProposal)
    }
}
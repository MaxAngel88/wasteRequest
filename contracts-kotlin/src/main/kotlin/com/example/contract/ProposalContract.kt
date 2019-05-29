package com.example.contract

import com.example.state.ProposalState
import com.example.state.WasteRequestState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.util.*


class ProposalContract : Contract {
    companion object {
        @JvmStatic
        val PROPOSAL_CONTRACT_ID = "com.example.contract.ProposalContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands) {
            val setOfSigners = command.signers.toSet()
            when (command.value) {
                is Commands.Create -> verifyCreate(tx, setOfSigners)
                is Commands.End -> verifyEnd(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    interface Commands : CommandData {
        class Create : Commands, TypeOnlyCommandData()
        class End : Commands, TypeOnlyCommandData()
        //class Settle : TypeOnlyCommandData(), Commands
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

        "No inputs should be consumed when creating a transaction." using (tx.inputStates.isEmpty())

        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val proposal = tx.outputsOfType<ProposalState>().single()
        "issuer and counterpart cannot be the same" using (proposal.cliente != proposal.fornitore)
        "codCliente cannot be empty" using (proposal.codCliente.isNotEmpty())
        "codFornitore cannot be empty" using (proposal.codFornitore.isNotEmpty())
        "validity must be grather than date" using (proposal.validity > proposal.requestDate)
        "wasteType cannot be empty" using (proposal.wasteType.isNotEmpty())
        "wasteWeight must be grather than 0" using (proposal.wasteWeight > 0.0)
        "wasteGps cannot be empty" using (proposal.wasteGps.isNotEmpty())
        "proposal status must be 'pending' or 'denied'" using (proposal.status.equals("pending", ignoreCase = true) || proposal.status.equals("denied", ignoreCase = true))

        "All of the participants must be signers." using (signers.containsAll(proposal.participants.map { it.owningKey }))
    }

    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "one input when ending a proposal." using (tx.inputStates.size == 1)
        "no proposal state should be created." using (tx.outputStates.isEmpty())
        val proposal = tx.inputsOfType<ProposalState>().single()
        "All of the participants must be signers." using (signers.containsAll(proposal.participants.map { it.owningKey }))
    }
}
package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.POJO.ProposalPojo
import com.example.flow.ProposalFlow.Starter
import com.example.flow.ProposalFlow.Acceptor
import com.example.flow.ProposalFlow.AcceptorEnd
import com.example.flow.ProposalFlow.EndProposal
import com.example.contract.ProposalContract
import com.example.contract.ProposalContract.Companion.PROPOSAL_CONTRACT_ID
import com.example.state.ProposalState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*
import khttp.get
import khttp.post

/**
 * MAKE A SAME COPY OF THIS PACKAGE UNDER: contract-kotlin/src/com.example/ TO PERMIT IMPORT OF PROPOSALFLOW
 *
 **/


object ProposalFlow {

    @InitiatingFlow
    @StartableByRPC
    class Starter(
            val fornitore: Party,
            val syndial: Party,
            val properties: ProposalPojo) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val proposalState = ProposalState(
                    serviceHub.myInfo.legalIdentities.first(),
                    fornitore,
                    syndial,
                    properties.codCliente,
                    properties.codFornitore,
                    properties.requestDate,
                    properties.wasteType,
                    properties.wasteWeight,
                    properties.wasteDesc,
                    properties.wasteDescAmm,
                    properties.wasteGps,
                    properties.status,
                    properties.validity,
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(ProposalContract.Commands.Create(), proposalState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(proposalState, PROPOSAL_CONTRACT_ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            var syndialFlow : FlowSession = initiateFlow(syndial)
            var fornitoreFlow : FlowSession = initiateFlow(fornitore)

            // Send the state to the fornitore, and receive it back with their signature.

            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(syndialFlow, fornitoreFlow), GATHERING_SIGS.childProgressTracker()))


            //DEBUG
            //logger.info(get("http://httpbin.org/ip").jsonObject.getString("origin"))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.

            return subFlow(FinalityFlow(fullySignedTx, setOf(syndialFlow, fornitoreFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Starter::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a transaction." using (output is ProposalState)

                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartyFlow, expectedTxId = txId))
        }
    }

    @InitiatingFlow
    @SchedulableFlow
    class EndProposal(private val stateRef: StateRef) : FlowLogic<Unit>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call() {

            // Obtain a reference to the notary we want to use.

            val stateAndRef = serviceHub.toStateAndRef<ProposalState>(stateRef)
            val proposalState = stateAndRef.state.data

            // Stage 1.
            if(serviceHub.myInfo.legalIdentities.first() == proposalState.cliente){

                val notary = serviceHub.networkMapCache.notaryIdentities[0]
                progressTracker.currentStep = GENERATING_TRANSACTION
                // Generate an unsigned transaction.

                val txCommand = Command(ProposalContract.Commands.End(), proposalState.participants.map { it.owningKey })
                val txBuilder = TransactionBuilder(notary)
                        .addInputState(stateAndRef)
                        .addCommand(txCommand)

                progressTracker.currentStep = VERIFYING_TRANSACTION
                // Verify that the transaction is valid.
                txBuilder.verify(serviceHub)

                // Stage 3.
                progressTracker.currentStep = SIGNING_TRANSACTION
                // Sign the transaction.
                val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

                // Stage 4.
                progressTracker.currentStep = GATHERING_SIGS


                var syndialFlow : FlowSession = initiateFlow(proposalState.syndial)
                var fornitoreFlow : FlowSession = initiateFlow(proposalState.fornitore)

                // Send the state to the counterparty, and receive it back with their signature.

                val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(syndialFlow, fornitoreFlow), GATHERING_SIGS.childProgressTracker()))

                // Stage 5.
                progressTracker.currentStep = FINALISING_TRANSACTION
                // Notarise and record the transaction in both parties' vaults.
                subFlow(FinalityFlow(fullySignedTx, setOf(syndialFlow, fornitoreFlow), FINALISING_TRANSACTION.childProgressTracker()))
                logger.info("stop scheduled end for "+ proposalState.linearId.id.toString())
            }
        }
    }


    @InitiatedBy(EndProposal::class)
    class AcceptorEnd(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                }
            }

            return subFlow(signTransactionFlow)
        }
    }
}
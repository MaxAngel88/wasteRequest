package com.example.server

import com.example.POJO.IssueWasteRequestPojo
import com.example.POJO.ProposalPojo
import com.example.POJO.ResponsePojo
import com.example.POJO.WasteRequestPojo
import com.example.flow.ExampleFlow.Initiator
import com.example.flow.ProposalFlow
import com.example.flow.WasteRequestFlow
import com.example.schema.ProposalSchemaV1
import com.example.schema.WasteRequestSchemaV1
import com.example.state.IOUState
import com.example.state.ProposalState
import com.example.state.WasteRequestState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.text.SimpleDateFormat
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.DefaultValue
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response
import java.util.*
import net.corda.core.node.services.vault.QueryCriteria
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import net.corda.core.messaging.CordaRPCOps

import javax.ws.rs.*


val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/api/example/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpcOps: CordaRPCOps) {


    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpcOps.nodeInfo().legalIdentities.first().name
    private val proxy = rpcOps

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all IOU states that exist in the node's vault.
     */
    @GetMapping(value = [ "ious" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getIOUs() : ResponseEntity<List<StateAndRef<IOUState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<IOUState>().states)
    }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /spring/api/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */

    @PostMapping(value = [ "create-iou" ], produces = [ TEXT_PLAIN_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun createIOU(request: HttpServletRequest): ResponseEntity<String> {
        val iouValue = request.getParameter("iouValue").toInt()
        val partyName = request.getParameter("partyName")
        if(partyName == null){
            return ResponseEntity.badRequest().body("Query parameter 'partyName' must not be null.\n")
        }
        if (iouValue <= 0 ) {
            return ResponseEntity.badRequest().body("Query parameter 'iouValue' must be non-negative.\n")
        }
        val partyX500Name = CordaX500Name.parse(partyName)
        val otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name) ?: return ResponseEntity.badRequest().body("Party named $partyName cannot be found.\n")

        return try {
            val signedTx = proxy.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }

    /**
     * Displays all IOU states that only this node has been involved in.
     */
    @GetMapping(value = [ "my-ious" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMyIOUs(): ResponseEntity<List<StateAndRef<IOUState>>>  {
        val myious = proxy.vaultQueryBy<IOUState>().states.filter { it.state.data.lender.equals(proxy.nodeInfo().legalIdentities.first()) }
        return ResponseEntity.ok(myious)
    }

    /** ______________________________________________________________________________________________________________________  **/

    /**
     *
     * Proposal API List.
     *
     */

    /**
     *  GetAllProposalByParam
     */
    @GetMapping(value = ["proposalAPI/get/getAllProposal"], produces = [APPLICATION_JSON_VALUE])
    fun getAllProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                @DefaultValue("") @QueryParam("idProposal") idProposal: String,
                                @DefaultValue("") @QueryParam("codFornitore") codFornitore: String,
                                @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                @DefaultValue("pending") @QueryParam("status") status: String,
                                @DefaultValue("unconsumed") @QueryParam("statusPropBC") statusPropBC: String): Response {

        try{
            var myPage = page

            if(myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when (statusPropBC){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            val results = builder {

                var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)


                if(idProposal.length > 0){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(codFornitore.length > 0){
                    val idEqual = ProposalSchemaV1.PersistentProposal::codFornitore.equal(codFornitore)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.length > 0 && to.length > 0){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    val myFrom = format.parse(from)
                    val myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(status.length > 0){
                    val statusEqual = ProposalSchemaV1.PersistentProposal::status.equal(status)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(statusEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }

    /**
     *  GetAllMyProposal
     */
    @GetMapping(value = ["proposalAPI/get/getAllReceivedProposal"], produces = [APPLICATION_JSON_VALUE])
    fun getReceivedProposalsByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                     @DefaultValue("") @QueryParam("idProposal") idProposal: String,
                                     @DefaultValue("") @QueryParam("codCliente") codCliente: String,
                                     @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                     @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                     @DefaultValue("pending") @QueryParam("status") status: String,
                                     @DefaultValue("unconsumed") @QueryParam("statusPropBC") statusPropBC: String): Response {

        try{
            var myPage = page

            if(myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(statusPropBC){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            val result = builder {
                var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)

                val fornitoreEqual = ProposalSchemaV1.PersistentProposal::fornitore.equal(myLegalName.toString())
                val firstCriteria = QueryCriteria.VaultCustomQueryCriteria(fornitoreEqual, myStatus)
                criteria = criteria.and(firstCriteria)

                if(idProposal.length > 0){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(idProposal)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(codCliente.length > 0){
                    val idEqual = ProposalSchemaV1.PersistentProposal::codCliente.equal(codCliente)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.length > 0 && to.length > 0){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = ProposalSchemaV1.PersistentProposal::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(status.length > 0){
                    val statusEqual = ProposalSchemaV1.PersistentProposal::status.equal(status)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(statusEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<ProposalState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }
        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }

    /**
     *  InsertProposal
     */
    @PostMapping(value = ["proposalAPI/post/insertProposal"],consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun createProposal(req : ProposalPojo): Response {

        try{
            val fornitore : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(req.fornitore))!!
            val syndial: Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Syndial,L=Milan,C=IT"))!!

            val signedTx = proxy.startTrackedFlow(
                    ProposalFlow::Starter,
                    fornitore,
                    syndial,
                    req
            ).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction "+signedTx.toString()+" committed to ledger.")
            return Response.status(CREATED).entity(resp).build()

        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *  IssueWasteRequest
     */
    @PostMapping(value = ["proposalAPI/post/issueWasteRequest"], consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun issueWasteRequest(req : IssueWasteRequestPojo): Response {

        try {
            val signedTx = proxy.startTrackedFlow(
                    WasteRequestFlow::Issuer,
                    req.id
            ).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction "+signedTx.toString()+" committed to ledger.")
            return Response.status(CREATED).entity(resp).build()

        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }

    /**
     *
     * WasteRequest API List.
     *
     */

    /**
     *  GetAllWasteRequestByParam
     */
    @GetMapping(value = ["wasteRequestAPI/get/getAllWasteRequest"], produces = [APPLICATION_JSON_VALUE])
    fun getWasteRequestByParams(@DefaultValue("1") @QueryParam("page") page: Int,
                                @DefaultValue("") @QueryParam("wasteType") wasteType: String,
                                @DefaultValue("") @QueryParam("cliente") cliente: String,
                                @DefaultValue("") @QueryParam("fornitore") fornitore: String,
                                @DefaultValue("") @QueryParam("idWasteRequest") idWasteRequest: String,
                                @DefaultValue("") @QueryParam("wasteGps") wasteGps: String,
                                @DefaultValue("1990-01-01") @QueryParam("from") from: String,
                                @DefaultValue("2050-12-31") @QueryParam("to") to: String,
                                @DefaultValue("pending") @QueryParam("status") status: String,
                                @DefaultValue("unconsumed") @QueryParam("statusWasteReqBC") statusWasteReqBC: String): Response{

        try{
            var myPage = page

            if(myPage < 1){
                myPage = 1
            }

            var myStatus = Vault.StateStatus.UNCONSUMED

            when(statusWasteReqBC){
                "consumed" -> myStatus = Vault.StateStatus.CONSUMED
                "all" -> myStatus = Vault.StateStatus.ALL
            }

            var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(myStatus)
            val results = builder {

                if(idWasteRequest.length > 0){
                    val customCriteria = QueryCriteria.LinearStateQueryCriteria( uuid = listOf(UUID.fromString(idWasteRequest)), status = myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(cliente.length >0){
                    val clienteEqual = WasteRequestSchemaV1.PersistentWasteRequest::clienteName.equal(cliente)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(clienteEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(fornitore.length >0){
                    val fornitoreEqual = WasteRequestSchemaV1.PersistentWasteRequest::fornitoreName.equal(fornitore)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(fornitoreEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(wasteType.length >0){
                    val wasteTypeEqual = WasteRequestSchemaV1.PersistentWasteRequest::wasteType.equal(wasteType)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(wasteTypeEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(wasteGps.length >0){
                    val wasteGpsEqual = WasteRequestSchemaV1.PersistentWasteRequest::wasteGps.equal(wasteGps)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(wasteGpsEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(from.length > 0 && to.length > 0){
                    val format = SimpleDateFormat("yyyy-MM-dd")
                    var myFrom = format.parse(from)
                    var myTo = format.parse(to)
                    var dateBetween = WasteRequestSchemaV1.PersistentWasteRequest::requestDate.between(myFrom.toInstant(), myTo.toInstant())
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(dateBetween, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                if(status.length > 0){
                    val statusEqual = WasteRequestSchemaV1.PersistentWasteRequest::status.equal(status)
                    val customCriteria = QueryCriteria.VaultCustomQueryCriteria(statusEqual, myStatus)
                    criteria = criteria.and(customCriteria)
                }

                val results = proxy.vaultQueryBy<WasteRequestState>(
                        criteria,
                        PageSpecification(myPage, DEFAULT_PAGE_SIZE),
                        Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
                ).states

                return Response.ok(results).build()
            }

        }catch (ex: Exception){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }

    }

    /**
     *  Insert WasteRequest
     */
    @PostMapping(value = ["wasteRequestAPI/post/insertWasteRequest"], consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun createWasteRequest(req : WasteRequestPojo): Response {

        try {
            val cliente : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(req.cliente))!!
            val fornitore : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(req.fornitore))!!
            val syndial : Party = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Syndial,L=Milan,C=IT"))!!

            val signedTx = proxy.startTrackedFlow(
                    WasteRequestFlow::Starter,
                    cliente,
                    fornitore,
                    syndial,
                    req
            ).returnValue.getOrThrow()

            val resp = ResponsePojo("SUCCESS", "transaction "+signedTx.toString()+" committed to ledger.")
            return Response.status(CREATED).entity(resp).build()

        }catch (ex: Throwable){
            val msg = ex.message
            logger.error(ex.message, ex)
            val resp = ResponsePojo("ERROR", msg!!)
            return Response.status(BAD_REQUEST).entity(resp).build()
        }
    }
}

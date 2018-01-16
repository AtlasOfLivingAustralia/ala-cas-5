package au.org.ala.cas.webflow

import au.org.ala.utils.logger
import org.apereo.cas.authentication.AuthenticationException
import org.apereo.cas.ticket.InvalidTicketException
import org.apereo.cas.ticket.registry.TicketRegistrySupport
import org.apereo.cas.web.support.CookieRetrievingCookieGenerator
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

open class GenerateAuthCookieAction(val ticketRegistrySupport: TicketRegistrySupport,
                                    val alaProxyAuthenticationCookieGenerator: CookieRetrievingCookieGenerator) : AbstractAction() {

    companion object {
        val log = logger<GenerateAuthCookieAction>()
    }

    override fun doExecute(context: RequestContext): Event {

        log.error("GenerateAuthCookieAction running")
        //
        // Create ALA specific cookie that any ALA web application can read
        //
        val ticketGrantingTicket = WebUtils.getTicketGrantingTicketId(context)
        log.debug("Ticket-granting ticket found in the context is [{}]", ticketGrantingTicket)

        val authentication = this.ticketRegistrySupport.getAuthenticationFrom(ticketGrantingTicket) ?: throw InvalidTicketException(AuthenticationException("No authentication found for ticket " + ticketGrantingTicket), ticketGrantingTicket)
        val email = authentication.principal.id

        alaProxyAuthenticationCookieGenerator.addCookie(context, email)

        return success()
    }

}
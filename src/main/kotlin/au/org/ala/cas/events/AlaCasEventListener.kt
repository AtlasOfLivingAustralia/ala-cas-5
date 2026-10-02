package au.org.ala.cas.events

import au.org.ala.cas.alaUserId
import au.org.ala.cas.jdbc.AlaUserJdbcService
import au.org.ala.cas.stringAttribute
import au.org.ala.utils.logger
import org.apereo.cas.support.events.ticket.CasTicketGrantingTicketCreatedEvent
import org.apereo.services.persondir.IPersonAttributeDao
import org.apereo.services.persondir.support.CachingPersonAttributeDaoImpl
import org.springframework.context.event.EventListener
import java.util.concurrent.ExecutorService

open class AlaCasEventListener(
    val alaUserJdbcService: AlaUserJdbcService,
    val executorService: ExecutorService,
    val cachingAttributeRepository: IPersonAttributeDao //CachingPersonAttributeDaoImpl
) {

    companion object {
        val log = logger()
    }

    @EventListener
    open fun handleCasTicketGrantingTicketCreatedEvent(casTicketGrantingTicketCreatedEvent: CasTicketGrantingTicketCreatedEvent) {
        val authentication = casTicketGrantingTicketCreatedEvent.ticketGrantingTicket?.authentication
        log.debug("Handling CAS TGT created event for : {}", authentication)
        val userid = authentication?.alaUserId()
        if (userid != null) {
            executorService.execute {
                try {
                    alaUserJdbcService.updateLastLogin(userid)
//                    email?.let { cachingAttributeRepository.removeUserAttributes(it) }
                } catch (e: Exception) {
                    log.error("Couldn't update last login time for {}", userid, e)
                }
            }
        }
    }

}

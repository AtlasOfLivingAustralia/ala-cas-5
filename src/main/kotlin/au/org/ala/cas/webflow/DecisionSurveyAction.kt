package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.alaUserId
import au.org.ala.cas.jdbc.AlaUserJdbcService
import au.org.ala.utils.logger
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

/**
 * Webflow Action to decide whether to offer the user a survey or not
 */
class DecisionSurveyAction(
    val alaCasProperties: AlaCasProperties,
    val alaUserJdbcService: AlaUserJdbcService
) : AbstractAction() {

    companion object {
        val log = logger()
    }

    override fun doExecute(context: RequestContext?): Event {
        log.debug("DecisionSurveyAction.doExecute")
        val authentication = WebUtils.getAuthentication(context)
        val userid: Long? = authentication.alaUserId()

        if (userid == null) {
            log.warn("Couldn't extract userid from {}, not offering survey", authentication)
            return no()
        }

        if (authentication?.principal != null) {
            val offerSurvey = alaUserJdbcService.shouldOfferSurvey(userid)
            log.debug("Survey decision for {}: {}", userid, offerSurvey)
            return if (offerSurvey) yes() else no()
        }
        return no()
    }
}

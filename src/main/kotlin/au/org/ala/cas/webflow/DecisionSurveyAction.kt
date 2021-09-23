package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.alaUserId
import au.org.ala.utils.logger
import org.apereo.cas.web.support.WebUtils
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext
import javax.sql.DataSource

/**
 * Decide whether to offer the user a survey or not
 */
class DecisionSurveyAction(
    val alaCasProperties: AlaCasProperties,
    val dataSource: DataSource
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

            val template = NamedParameterJdbcTemplate(dataSource)
            val affiliationCount = try {
                template.queryForObject(
                    alaCasProperties.userCreator.jdbc.countExtraAttributeSql,
                    mapOf("userid" to userid, "name" to "affiliation"),
                    Integer::class.java
                )
            } catch(e: EmptyResultDataAccessException) {
                0
            }
            log.debug("Survey decision for {}: {}", userid, affiliationCount)
            return if (affiliationCount == 0) yes() else no()
        }
        return no()
    }
}
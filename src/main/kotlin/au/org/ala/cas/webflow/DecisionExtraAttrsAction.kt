package au.org.ala.cas.webflow

import au.org.ala.cas.stringAttribute
import au.org.ala.utils.logger
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

/**
 * Webflow action to decide whether to show the user a city / state / country / organisation form.
 * Logic is, if the user has never logged in before and does not have city / state / country or organisation set then
 * show them the form.
 */
class DecisionExtraAttrsAction : AbstractAction() {

    companion object {
        val log = logger()
        const val NULL_ATTRIBUTE_VALUE = "null"
    }

    override fun doExecute(context: RequestContext?): Event {

        val authentication = WebUtils.getAuthentication(context)
        if (authentication?.principal != null) {
            val principal = authentication.principal
            val attributes = principal.attributes

            // all attributes are strings, including "null" and dates
            // date format appears to be yyyy-MM-dd hh:mm:ss local time (db?  server?) eg 2018-05-30 17:07:46
            log.debug("ExtraAttrs Decision State attributes: {}", principal.attributes)
            val lastLogin = principal.stringAttribute("lastLogin")
            val city = principal.stringAttribute("city")
            val state = principal.stringAttribute("state")
            val organisation = principal.stringAttribute("organisation")
            if (lastLogin.isNullOrBlank() || lastLogin == NULL_ATTRIBUTE_VALUE) {
                if (!city.isNullOrBlank() || !state.isNullOrBlank() || !organisation.isNullOrBlank()) {
                    log.info("Last login for {} is null but the user already has a city, state or organisation set", attributes["email"])
                    return no()
                }
                log.info("Last login for {} is blank or null: {}", attributes["email"], lastLogin)
                return yes()
            }
        }
        return no()
    }
}
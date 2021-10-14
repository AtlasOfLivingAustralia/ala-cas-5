package au.org.ala.cas.webflow

import au.org.ala.cas.*
import au.org.ala.utils.logger
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext
import javax.sql.DataSource

/**
 * Webflow Action to save the user survey form.
 */
open class SaveSurveyAction(
    val alaCasProperties: AlaCasProperties,
    val dataSource: DataSource,
    private val extraAttributesService: ExtraAttributesService
) : AbstractAction() {


    companion object {
        val log = logger()

        const val SURVEY_FLOW_VAR = "survey"
    }

    override fun doExecute(context: RequestContext): Event {
        log.debug("SaveSurveyAction.doExecute()")
        val authentication = WebUtils.getAuthentication(context)
        val userid: Long? = authentication.alaUserId()

        if (userid == null) {
            log.warn("Couldn't extract userid from {}, skipping action", authentication)
            return success()
        }

        val surveyAttrs =
            context.flowScope[SURVEY_FLOW_VAR] as? Survey

        if (surveyAttrs == null) {
            log.warn("Couldn't find survey in flow scope, skipping action")
            return success()
        }

        extraAttributesService.updateUserSurveyResult(userid, surveyAttrs)

        context.flowScope.remove(SURVEY_FLOW_VAR)
        return success()
    }

}

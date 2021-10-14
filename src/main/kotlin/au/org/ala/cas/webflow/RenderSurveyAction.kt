package au.org.ala.cas.webflow

import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

/**
 * Webflow Action that runs before the User Survey (userAffiliationSurveyForm.html) is shown to the user
 * Used to put list into view scope.
 */
class RenderSurveyAction(private val extraAttributesService: ExtraAttributesService) : AbstractAction() {

    override fun doExecute(context: RequestContext): Event {
        logger.debug("RenderSurveyAction.doExecute()")

        val locale = context.externalContext.locale
        context.viewScope.put("affiliations",
            extraAttributesService.surveyOptions(locale)
        )
        return success()
    }

}
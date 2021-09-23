package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

class EnterSurveyAction(val alaCasProperties: AlaCasProperties) : AbstractAction() {

    override fun doExecute(context: RequestContext): Event {
        logger.debug("EnterSurveyAction.doExecute")
        context.flowScope.put(SaveSurveyAction.SURVEY_FLOW_VAR, Survey())
        return success()
    }

}
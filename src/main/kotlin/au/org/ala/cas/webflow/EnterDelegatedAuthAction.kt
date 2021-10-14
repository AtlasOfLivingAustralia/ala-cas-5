package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

/**
 * WebFlow action called on entering the delegated auth extra attributes form.  Adds the form bean into the flow scope.
 */
class EnterDelegatedAuthAction(val alaCasProperties: AlaCasProperties) : AbstractAction() {
    override fun doExecute(context: RequestContext): Event {
        val authentication = WebUtils.getAuthentication(context)
        val extraAttrs = ExtraAttrs.fromMap(authentication.principal.attributes)
        if (extraAttrs.country.isBlank()) extraAttrs.country = alaCasProperties.userCreator.defaultCountry

        if (alaCasProperties.userCreator.enableUserSurvey) {
            extraAttrs.survey = Survey()
//            context.flowScope.put(SaveSurveyAction.SURVEY_FLOW_VAR, Survey())
        }

        context.flowScope.put(SaveExtraAttrsAction.EXTRA_ATTRS_FLOW_VAR, extraAttrs)


        return success()
    }

}
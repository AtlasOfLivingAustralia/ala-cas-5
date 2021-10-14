package au.org.ala.cas.webflow

import au.org.ala.cas.*
import au.org.ala.utils.logger
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext
import javax.sql.DataSource

/**
 * Webflow Action used to save the extra attributes form.
 * Includes logic to save the survey if it's offered in combination
 */
open class SaveExtraAttrsAction(
    val alaCasProperties: AlaCasProperties,
    val dataSource: DataSource,
    private val extraAttributesService: ExtraAttributesService) : AbstractAction() {


    companion object {
        val log = logger()

        const val EXTRA_ATTRS_FLOW_VAR = "attrs"
    }

    override fun doExecute(context: RequestContext): Event {

//        val credential = WebUtils.getCredential(context, ClientCredential::class.java)
        val authentication = WebUtils.getAuthentication(context)
        val userid: Long? = authentication.alaUserId()

        if (userid == null) {
            log.warn("Couldn't extract userid from {}, aborting", authentication)
            return success()
        }

        val extraAttrs =
            context.flowScope[EXTRA_ATTRS_FLOW_VAR] as? ExtraAttrs

        if (extraAttrs == null) {
            log.warn("Couldn't find extraAttrs in flow scope, aborting")
            return success()
        }

        extraAttributesService.updateExtraAttrs(userid, authentication, extraAttrs)

        if (alaCasProperties.userCreator.enableUserSurvey) {
            val survey = extraAttrs.survey
            if (survey != null) {
                extraAttributesService.updateUserSurveyResult(userid, survey)
            } else {
                log.warn("Enable Users Survey is enabled but the extraAttrs.survey is null")
            }
        }

        return success(context)
    }

    fun success(context: RequestContext): Event {
        return super.success().also {
            context.flowScope.remove(EXTRA_ATTRS_FLOW_VAR)
        }
    }

}

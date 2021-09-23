package au.org.ala.cas.webflow

import au.org.ala.cas.*
import au.org.ala.utils.logger
import org.apereo.cas.web.support.WebUtils
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext
import javax.sql.DataSource

open class SaveSurveyAction(
    val alaCasProperties: AlaCasProperties,
    val dataSource: DataSource,
    transactionManager: DataSourceTransactionManager
) : AbstractAction() {


    companion object {
        val log = logger()

        const val SURVEY_FLOW_VAR = "survey"

        const val AFFILIATION = "affiliation"
    }

    val transactionTemplate: TransactionTemplate = TransactionTemplate(transactionManager)

    override fun doExecute(context: RequestContext): Event {
        log.debug("SaveSurveyAction.doExecute()")
        val authentication = WebUtils.getAuthentication(context)
        val userid: Long? = authentication.alaUserId()

        if (userid == null) {
            log.warn("Couldn't extract userid from {}, aborting", authentication)
            return success()
        }

        val surveyAttrs =
            context.flowScope[SURVEY_FLOW_VAR] as? Survey

        if (surveyAttrs == null) {
            log.warn("Couldn't find extraAttrs in flow scope, aborting")
            return success()
        }

        transactionTemplate.execute { status ->
            try {
                val template = NamedParameterJdbcTemplate(dataSource)

                updateField(template, userid, AFFILIATION, surveyAttrs.affiliation)

            } catch (e: Exception) {
                // If we can't save the survey, just log and move on because we don't want to
                // prevent the user from actually logging in
                log.warn("Rolling back transaction because of exception", e)
                status.setRollbackOnly()
            }
        }

        context.flowScope.remove(SURVEY_FLOW_VAR)
        return success()
    }

    private fun updateField(template: NamedParameterJdbcTemplate, userid: Long, name: String, value: String) {
        val params = mapOf("userid" to userid, "name" to name, "value" to value)
        val result = template.queryForObject(alaCasProperties.userCreator.jdbc.countExtraAttributeSql, params, Integer::class.java)
        val updateCount = if (result > 0) {
            template.update(alaCasProperties.userCreator.jdbc.updateExtraAttributeSql, params)
        } else {
            template.update(alaCasProperties.userCreator.jdbc.insertExtraAttributeSql, params)
        }
        if (updateCount != 1) {
            log.warn("Insert / update field for {}, {}, {} returned {} updates", userid, name, value, updateCount)
        }
    }

}

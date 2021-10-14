package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.setSingleAttributeValue
import au.org.ala.cas.stringAttribute
import au.org.ala.utils.logger
import org.apereo.cas.authentication.Authentication
import org.apereo.services.persondir.support.CachingPersonAttributeDaoImpl
import org.springframework.context.MessageSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import javax.sql.DataSource

/**
 * Service for interacting with extra attributes on a users profile.
 */
@Component
class ExtraAttributesService(
    private val alaCasProperties: AlaCasProperties,
    private val dataSource: DataSource,
    transactionManager: DataSourceTransactionManager,
    private val cachingAttributeRepository: CachingPersonAttributeDaoImpl,
    private val messageSource: MessageSource
) {

    companion object {
        val log = logger()

        const val ORGANISATION = "organisation"
        const val CITY = "city"
        const val STATE = "state"
        const val COUNTRY = "country"

        const val AFFILIATION = "affiliation"

    }

    private val transactionTemplate: TransactionTemplate = TransactionTemplate(transactionManager)

    fun updateExtraAttrs(userid: Long, authentication: Authentication, extraAttrs: ExtraAttrs) {
        val email = authentication.stringAttribute("email")

        transactionTemplate.execute { status ->
            try {
                val template = NamedParameterJdbcTemplate(dataSource)
                listOf(ORGANISATION to extraAttrs.organisation,
                    CITY to extraAttrs.city,
                    STATE to extraAttrs.state,
                    COUNTRY to extraAttrs.country
                ).forEach { (name, value) ->
                    updateField(template, userid, authentication, name, value)
                }

                // invalidate cache for the new user attributes
                // TODO there must be a less coupled way of achieving this

                email?.let { cachingAttributeRepository.removeUserAttributes(it) }
            } catch (e: Exception) {
                // If we can't set the properties, just log and move on
                // because none of these properties are required.
                log.warn("Rolling back transaction because of exception", e)
                status.setRollbackOnly()
//                throw e
            }
        }

    }

    fun updateUserSurveyResult(userid: Long, survey: Survey) {
        transactionTemplate.execute { status ->
            try {
                val template = NamedParameterJdbcTemplate(dataSource)

                updateField(template, userid, AFFILIATION, survey.affiliation)

            } catch (e: Exception) {
                // If we can't save the survey, just log and move on because we don't want to
                // prevent the user from actually logging in
                log.warn("Rolling back transaction because of exception", e)
                status.setRollbackOnly()
            }
        }
    }

    /**
     * Return the survey options for a given locale
     */
    fun surveyOptions(locale: Locale): Map<String, String> {
        val args = emptyArray<String>()
        return linkedMapOf(
            "" to messageSource.getMessage("ala.affiliations.noneSelected", args, "-- Please select one --", locale),
            "community" to messageSource.getMessage("ala.affiliations.community", args, "Community based organisation – club, society, landcare", locale),
            "education" to messageSource.getMessage("ala.affiliations.education", args, "Education – primary and secondary schools, TAFE, environmental or wildlife education", locale),
            "firstNationsOrg" to messageSource.getMessage("ala.affiliations.firstNationsOrg", args, "First Nations organisation", locale),
            "government" to messageSource.getMessage("ala.affiliations.government", args, "Government – federal, state and local", locale),
            "industry" to messageSource.getMessage("ala.affiliations.industry", args, "Industry, commercial, business or retail", locale),
            "mri" to messageSource.getMessage("ala.affiliations.mri", args, "Medical Research Institute (MRI)", locale),
            "museum" to messageSource.getMessage("ala.affiliations.museum", args, "Museum, herbarium, library, botanic gardens", locale),
            "nfp" to messageSource.getMessage("ala.affiliations.nfp", args, "Not for profit", locale),
            "otherResearch" to messageSource.getMessage("ala.affiliations.otherResearch", args, "Other research organisation, unaffiliated researcher", locale),
            "private" to messageSource.getMessage("ala.affiliations.private", args, "Private user", locale),
            "publiclyFunded" to messageSource.getMessage("ala.affiliations.publiclyFunded", args, "Publicly Funded Research Agency (PFRA) e.g. CSIRO, AIMS, DSTO", locale),
            "uniResearch" to messageSource.getMessage("ala.affiliations.uniResearch", args, "University – faculty, researcher", locale),
            "uniGeneral" to messageSource.getMessage("ala.affiliations.uniGeneral", args, "University - general staff, administration, management", locale),
            "uniStudent" to messageSource.getMessage("ala.affiliations.uniStudent", args, "University – student", locale),
            "volunteer" to messageSource.getMessage("ala.affiliations.volunteer", args, "Volunteer, citizen scientist", locale),
            "wildlife" to messageSource.getMessage("ala.affiliations.wildlife", args, "Wildlife park, sanctuary, zoo, aquarium, wildlife rescue", locale),
            "other" to messageSource.getMessage("ala.affiliations.other", args, "Other", locale),
            "disinclinedToAcquiesce" to messageSource.getMessage("ala.affiliations.disinclinedToAcquiesce", args, "Prefer not to say", locale)
        )
    }

    private fun updateField(template: NamedParameterJdbcTemplate, userid: Long, authentication: Authentication, name: String, value: String) {
        if (value != authentication.stringAttribute(name)) {
            updateField(template, userid, name, value)
            authentication.principal.attributes.setSingleAttributeValue(name, value)
            if (authentication.attributes.containsKey(name)) authentication.attributes.setSingleAttributeValue(name, value)
        }
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
            SaveExtraAttrsAction.log.warn("Insert / update field for {}, {}, {} returned {} updates", userid, name, value, updateCount)
        }
    }


}

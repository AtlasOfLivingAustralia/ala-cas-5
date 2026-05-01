package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.jdbc.AlaUserJdbcService
import au.org.ala.cas.setSingleAttributeValue
import au.org.ala.cas.stringAttribute
import au.org.ala.utils.logger
import org.apereo.cas.authentication.Authentication
import org.apereo.services.persondir.IPersonAttributeDao
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.*

/**
 * Service for interacting with extra attributes on a users profile.
 */
@Component
class ExtraAttributesService(
    private val alaCasProperties: AlaCasProperties,
    private val alaUserJdbcService: AlaUserJdbcService,
    private val cachingAttributeRepository: IPersonAttributeDao, //CachingPersonAttributeDaoImpl,
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

    fun updateExtraAttrs(userid: Long, authentication: Authentication, extraAttrs: ExtraAttrs) {
        try {
            listOf(ORGANISATION to extraAttrs.organisation,
                CITY to extraAttrs.city,
                STATE to extraAttrs.state,
                COUNTRY to extraAttrs.country
            ).forEach { (name, value) ->
                updateField(userid, authentication, name, value)
            }

            // invalidate cache for the new user attributes
            // TODO there must be a less coupled way of achieving this

            // TODO Re-enable attribute caching
//                email?.let { cachingAttributeRepository.removeUserAttributes(it) }
        } catch (e: Exception) {
            // If we can't set the properties, just log and move on because none of these properties are required.
            log.warn("Couldn't update extra attributes", e)
//                throw e
        }

    }

    fun updateUserSurveyResult(userid: Long, survey: Survey) {
        try {
            updateField(userid, AFFILIATION, survey.affiliation)
        } catch (e: Exception) {
            // If we can't save the survey, just log and move on because we don't want to prevent the user from actually logging in
            log.warn("Couldn't update user survey result", e)
        }
    }

    /**
     * Return the survey options for a given locale
     */
    fun surveyOptions(locale: Locale): Map<String, String> {
        val args = emptyArray<String>()
        fun message(code: String, defaultMessage: String) = messageSource.getMessage(code, args, defaultMessage, locale) ?: defaultMessage

        return linkedMapOf<String, String>(
            "" to message("ala.affiliations.noneSelected", "-- Please select one --"),
            "community" to message("ala.affiliations.community", "Community based organisation – club, society, landcare"),
            "education" to message("ala.affiliations.education", "Education – primary and secondary schools, TAFE, environmental or wildlife education"),
            "firstNationsOrg" to message("ala.affiliations.firstNationsOrg", "First Nations organisation"),
            "government" to message("ala.affiliations.government", "Government – federal, state and local"),
            "industry" to message("ala.affiliations.industry", "Industry, commercial, business or retail"),
            "mri" to message("ala.affiliations.mri", "Medical Research Institute (MRI)"),
            "museum" to message("ala.affiliations.museum", "Museum, herbarium, library, botanic gardens"),
            "nfp" to message("ala.affiliations.nfp", "Not for profit"),
            "otherResearch" to message("ala.affiliations.otherResearch", "Other research organisation, unaffiliated researcher"),
            "private" to message("ala.affiliations.private", "Private user"),
            "publiclyFunded" to message("ala.affiliations.publiclyFunded", "Publicly Funded Research Agency (PFRA) e.g. CSIRO, AIMS, DSTO"),
            "uniResearch" to message("ala.affiliations.uniResearch", "University – faculty, researcher"),
            "uniGeneral" to message("ala.affiliations.uniGeneral", "University - general staff, administration, management"),
            "uniStudent" to message("ala.affiliations.uniStudent", "University – student"),
            "volunteer" to message("ala.affiliations.volunteer", "Volunteer, citizen scientist"),
            "wildlife" to message("ala.affiliations.wildlife", "Wildlife park, sanctuary, zoo, aquarium, wildlife rescue"),
            "other" to message("ala.affiliations.other", "Other"),
            "disinclinedToAcquiesce" to message("ala.affiliations.disinclinedToAcquiesce", "Prefer not to say")
        )
    }

    private fun updateField(userid: Long, authentication: Authentication, name: String, value: String) {
        if (value != authentication.stringAttribute(name)) {
            updateField(userid, name, value)
            authentication.principal.attributes.setSingleAttributeValue(name, value)
            if (authentication.attributes.containsKey(name)) authentication.attributes.setSingleAttributeValue(name, value)
        }
    }

    private fun updateField(userid: Long, name: String, value: String) {
        val updateCount = alaUserJdbcService.upsertProfile(userid, name, value)
        if (updateCount != 1) {
            SaveExtraAttrsAction.log.warn("Insert / update field for {}, {}, {} returned {} updates", userid, name, value, updateCount)
        }
    }


}

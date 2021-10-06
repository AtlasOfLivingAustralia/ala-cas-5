package au.org.ala.cas.webflow

import org.springframework.context.MessageSource
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

class RenderSurveyAction(private val messageSource: MessageSource) : AbstractAction() {

    override fun doExecute(context: RequestContext): Event {
        logger.debug("RenderSurveyAction.doExecute()")
        val args = emptyArray<String>()
        val locale = context.externalContext.locale
        context.viewScope.put("affiliations",
            mapOf(
                "" to messageSource.getMessage("ala.affiliations.noneSelected", args, "-- Please select one --", locale),
                "uniResearch" to messageSource.getMessage("ala.affiliations.uniResearch", args, "University – faculty, researcher", locale),
                "uniGeneral" to messageSource.getMessage("ala.affiliations.uniGeneral", args, "University - general staff, administration, management", locale),
                "uniStudent" to messageSource.getMessage("ala.affiliations.uniStudent", args, "University – student", locale),
                "education" to messageSource.getMessage("ala.affiliations.education", args, "Education – primary and secondary schools, TAFE, environmental or wildlife education", locale),
                "firstNationsOrg" to messageSource.getMessage("ala.affiliations.firstNationsOrg", args, "First Nations organisation", locale),
                "government" to messageSource.getMessage("ala.affiliations.government", args, "Government – federal, state and local", locale),
                "industry" to messageSource.getMessage("ala.affiliations.industry", args, "Industry, commercial, business or retail", locale),
                "museum" to messageSource.getMessage("ala.affiliations.museum", args, "Museum, herbarium, library, botanic gardens", locale),
                "wildlife" to messageSource.getMessage("ala.affiliations.wildlife", args, "Wildlife park, sanctuary, zoo, aquarium, wildlife rescue", locale),
                "publiclyFunded" to messageSource.getMessage("ala.affiliations.publiclyFunded", args, "Publicly Funded Research Agency (PFRA) includes CSIRO, ANSTO, AIMS, AAO, AIATSIS, AAD, DSTO, GA, BoM", locale),
                "mri" to messageSource.getMessage("ala.affiliations.mri", args, "Medical Research Institute (MRI)", locale),
                "otherResearch" to messageSource.getMessage("ala.affiliations.otherResearch", args, "Other research organisation, unaffiliated researcher", locale),
                "nfp" to messageSource.getMessage("ala.affiliations.nfp", args, "Not for profit", locale),
                "community" to messageSource.getMessage("ala.affiliations.community", args, "Community based organisation – club, society, landcare", locale),
                "volunteer" to messageSource.getMessage("ala.affiliations.volunteer", args, "Volunteer, citizen scientist", locale),
                "private" to messageSource.getMessage("ala.affiliations.private", args, "Private user", locale),
                "other" to messageSource.getMessage("ala.affiliations.other", args, "Other", locale),
                "disinclinedToAcquiesce" to messageSource.getMessage("ala.affiliations.disinclinedToAcquiesce", args, "Prefer not to say", locale)
            )

        )
        return success()
    }

}
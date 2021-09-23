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
                "uniStudent" to messageSource.getMessage("ala.affiliations.uniStudent", args, "University – student", locale),
                "uniGeneral" to messageSource.getMessage("ala.affiliations.uniGeneral", args, "University - general staff, library, administration, management", locale),
                "publiclyFunded" to messageSource.getMessage("ala.affiliations.publiclyFunded", args, "Publicly Funded Research Agency (PFRA)", locale),
                "mri" to messageSource.getMessage("ala.affiliations.mri", args, "Medical Research Institute (MRI)", locale),
                "museum" to messageSource.getMessage("ala.affiliations.museum", args, "Museum, herbarium, library or other collecting organisation", locale),
                "otherResearch" to messageSource.getMessage("ala.affiliations.otherResearch", args, "Other research organisation", locale),
                "industry" to messageSource.getMessage("ala.affiliations.industry", args, "Industry / commercial organisation", locale),
                "govLocal" to messageSource.getMessage("ala.affiliations.govLocal", args, "Government - local", locale),
                "govState" to messageSource.getMessage("ala.affiliations.govState", args, "Government - state", locale),
                "govFederal" to messageSource.getMessage("ala.affiliations.govFederal", args, "Government - federal", locale),
                "volunteer" to messageSource.getMessage("ala.affiliations.volunteer", args, "Volunteer including citizen scientists", locale),
                "education" to messageSource.getMessage("ala.affiliations.education", args, "Education – primary and secondary", locale),
                "nfp" to messageSource.getMessage("ala.affiliations.nfp", args, "Not for profit", locale),
                "other" to messageSource.getMessage("ala.affiliations.other", args, "Other / unaffiliated", locale),
                "disinclinedToAcquiesce" to messageSource.getMessage("ala.affiliations.disinclinedToAcquiesce", args, "Prefer not to say", locale)
            )
        )
        return success()
    }

}
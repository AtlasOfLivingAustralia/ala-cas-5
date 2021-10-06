package au.org.ala.cas.webflow

import au.org.ala.utils.logger
import org.hibernate.validator.constraints.NotBlank
import org.springframework.binding.message.MessageBuilder
import org.springframework.binding.validation.ValidationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.RequestContext
import org.thymeleaf.spring4.naming.SpringContextVariableNames
import org.springframework.webflow.execution.RequestContextHolder as WFRCH
import java.io.Serializable
import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletRequest

data class Survey @JvmOverloads constructor(
    @NotBlank
    var affiliation: String = ""
): Serializable {

    companion object {
        val log = logger()
    }

    /**
     * Spring Webflow validator, called dynamically by WebFlow - note this doesn't work with the Thymeleaf Spring MVC
     * validation integration.
     *
     * Must use thymeleaf constructs similar to
     * th:if="${#arrays.isEmpty(flowRequestContext.messageContext.getMessagesBySource('affiliation'))}"
     * to detect existence of validation errors on webflow return due to validation exception.
     *
     * (WebMVC uses errors object on the Spring WebMVC RequestContext, this RequestContext does not exist
     *  at this point in the webflow, possibly created for thymeleaf?)
     */
    fun validateUserAffiliationSurveyFormView(context: ValidationContext) {
        log.debug("validating survey {}", this)
        val messages = context.messageContext
        if (affiliation.isBlank()) {
            log.debug("Invalid affiliation")
            messages.addMessage(
                MessageBuilder()
                    .error()
                    .code("ala.screen.survey.affiliation.required")
                    .source("affiliation")
                    .defaultText("Affiliation is required")
                    .build()
            )
        }
    }

}
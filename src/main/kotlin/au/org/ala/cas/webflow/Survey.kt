package au.org.ala.cas.webflow

import au.org.ala.utils.logger
import org.hibernate.validator.constraints.NotBlank
import org.springframework.binding.message.MessageBuilder
import org.springframework.binding.validation.ValidationContext
import java.io.Serializable

/**
 * Form bean for the user survey
 */
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
    fun validate(context: ValidationContext) {
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
package au.org.ala.cas.rest

import au.org.ala.utils.logger
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import org.apereo.cas.authentication.principal.NullPrincipal
import org.apereo.cas.authentication.principal.Principal
import org.apereo.cas.authentication.principal.PrincipalResolver
import org.apereo.cas.support.oauth.web.endpoints.OAuth20ConfigurationContext
import org.apereo.cas.support.oauth.web.response.accesstoken.response.OAuth20JwtAccessTokenEncoder
import org.apereo.cas.ticket.accesstoken.OAuth20AccessToken
import org.apereo.cas.util.serialization.JacksonObjectMapperFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController("userAuthenticationResource")
class UserLookupResource(
//    val authenticationService: RestAuthenticationService,
    val configurationContext: OAuth20ConfigurationContext,
    val principalResolver: PrincipalResolver,
    val requiredScopes: List<String>
) {

    companion object {
        private val LOGGER = logger()
    }

    val expiredAccessTokenResponseEntity = ResponseEntity("expired_token", HttpStatus.UNAUTHORIZED)
    val unauthorizedResponseEntity = ResponseEntity("missing_access_token", HttpStatus.UNAUTHORIZED)

    @GetMapping(
        value = ["/v1/lookup"],
//        consumes = ["application/x-www-form-urlencoded", "application/json"],
        produces = ["application/json"]
    )
    fun authenticateRequest(
//        @RequestBody requestBody: MultiValueMap<String?, String?>,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<String> {
        return try {
            val accessToken = getAccessTokenFromRequest(request)

            return if (StringUtils.isBlank(accessToken)) {
                LOGGER.error("Missing [{}] from the request", "access_token")
                unauthorizedResponseEntity
            } else {
                val accessTokenTicket =
                    this.configurationContext.ticketRegistry.getTicket(
                        accessToken,
                        OAuth20AccessToken::class.java
                    )
                if (accessTokenTicket == null || accessTokenTicket.isExpired) {
                    LOGGER.error(
                        "Access token [{}] cannot be found in the ticket registry or has expired.",
                        accessToken
                    )
                    expiredAccessTokenResponseEntity
                } else if (!accessTokenTicket.scopes.containsAll(requiredScopes)) {
                    LOGGER.error(
                        "Access token [{}] missing required scopes: {}",
                        accessToken, requiredScopes
                    )
                    ResponseEntity("unauthorized", HttpStatus.UNAUTHORIZED)
                } else {
//                    AuthenticationCredentialsThreadLocalBinder.bindCurrent(accessTokenTicket.authentication)
                    updateAccessTokenUsage(accessTokenTicket)

//                    val map: Map<String, Any> = this.configurationContext.userProfileDataCreator.createFrom(accessTokenTicket, context)
//                    this.getConfigurationContext().getUserProfileViewRenderer().render(map, accessTokenTicket, response)
                    val user = request.getParameter("userLookup")
                    val principal = principalResolver.resolve { user }
                    if (principal == null || principal is NullPrincipal) {
                        LOGGER.info("user ({}) not found", user)
                        ResponseEntity("not_found", HttpStatus.NOT_FOUND)
                    } else {
                        build(principal)
                    }
                }
            }

        } catch (e: Exception) {
            LOGGER.error("Unexpected error", e)
            ResponseEntity(StringEscapeUtils.escapeHtml4(e.message), HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private val MAPPER = JacksonObjectMapperFactory.builder().defaultTypingEnabled(false).build().toObjectMapper()

    @Throws(java.lang.Exception::class)
    fun build(result: Principal): ResponseEntity<String> {
        return ResponseEntity(MAPPER.writeValueAsString(result), HttpStatus.OK)
    }

    protected fun getAccessTokenFromRequest(request: HttpServletRequest): String? {
        var accessToken = request.getParameter("access_token")
        if (StringUtils.isBlank(accessToken)) {
            val authHeader = request.getHeader("Authorization")
            if (StringUtils.isNotBlank(authHeader) && authHeader.lowercase(Locale.getDefault())
                    .startsWith("bearer".lowercase(Locale.getDefault()) + " ")
            ) {
                accessToken = authHeader.substring("bearer".length + 1)
            }
        }
        LOGGER.debug("[{}]: [{}]", "access_token", accessToken)
        return this.extractAccessTokenFrom(accessToken)
    }

    protected fun extractAccessTokenFrom(token: String?): String? {
        return OAuth20JwtAccessTokenEncoder.builder()
            .accessTokenJwtBuilder(this.configurationContext.accessTokenJwtBuilder).build().decode(token)
    }

    @Throws(java.lang.Exception::class)
    protected fun updateAccessTokenUsage(accessTokenTicket: OAuth20AccessToken) {
        accessTokenTicket.update()
        if (accessTokenTicket.isExpired) {
            this.configurationContext.ticketRegistry.deleteTicket(accessTokenTicket.id)
        } else {
            this.configurationContext.ticketRegistry.updateTicket(accessTokenTicket)
        }
    }

}
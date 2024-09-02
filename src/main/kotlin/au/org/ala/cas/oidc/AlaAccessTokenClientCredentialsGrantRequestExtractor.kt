package au.org.ala.cas.oidc

import org.apereo.cas.services.UnauthorizedServiceException
import org.apereo.cas.support.oauth.util.OAuth20Utils
import org.apereo.cas.support.oauth.web.endpoints.OAuth20ConfigurationContext
import org.apereo.cas.support.oauth.web.response.accesstoken.ext.AccessTokenClientCredentialsGrantRequestExtractor
import org.apereo.cas.support.oauth.web.response.accesstoken.ext.AccessTokenRequestContext
import org.pac4j.core.context.WebContext
import org.pac4j.core.profile.ProfileManager

/**
 * The default CAS AccessTokenClientCredentialsGrantRequestExtractor but patches in
 * claims to the principal object that will eventually end up in an access token JWT.
 */
class AlaAccessTokenClientCredentialsGrantRequestExtractor(oAuthConfigurationContext: OAuth20ConfigurationContext)
    : AccessTokenClientCredentialsGrantRequestExtractor(oAuthConfigurationContext) {

    override fun extractRequest(context: WebContext?): AccessTokenRequestContext {

        val clientId = configurationContext.requestParameterResolver
            .resolveClientIdAndClientSecret(context, configurationContext.sessionStore).key

        val manager = ProfileManager(context, configurationContext.sessionStore)
        val profile = manager.profile
        if (profile.isEmpty) {
            throw UnauthorizedServiceException("OAuth user profile cannot be determined")
        }
        val uProfile = profile.get()

        val registeredService = OAuth20Utils.getRegisteredOAuthServiceByClientId(configurationContext.servicesManager, clientId)
        // TODO port to here?
        val scopes = configurationContext.requestParameterResolver.resolveRequestScopes(context)

        val validScopes = determineValidScopes(registeredService, scopes)

        uProfile.addAuthenticationAttribute("scope", validScopes)
        uProfile.addAuthenticationAttribute("scopes", validScopes)
        uProfile.addAttribute("scope", validScopes)
        uProfile.addAttribute("scopes", validScopes)

        return super.extractRequest(context)
    }
}
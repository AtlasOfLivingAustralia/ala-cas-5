package au.org.ala.cas.oidc

import au.org.ala.cas.AlaCasProperties
import org.apereo.cas.authentication.principal.PrincipalFactory
import org.apereo.cas.authentication.principal.ServiceFactory
import org.apereo.cas.authentication.principal.WebApplicationService
import org.apereo.cas.configuration.CasConfigurationProperties
import org.apereo.cas.oidc.web.controllers.logout.OidcPostLogoutRedirectUrlMatcher
import org.apereo.cas.support.oauth.authenticator.OAuth20CasAuthenticationBuilder
import org.apereo.cas.support.oauth.profile.OAuth20ProfileScopeToAttributesFilter
import org.apereo.cas.support.oauth.web.OAuth20RequestParameterResolver
import org.apereo.cas.support.oauth.web.response.accesstoken.OAuth20TokenGenerator
import org.apereo.cas.ticket.accesstoken.OAuth20AccessTokenFactory
import org.apereo.cas.ticket.device.OAuth20DeviceTokenFactory
import org.apereo.cas.ticket.device.OAuth20DeviceUserCodeFactory
import org.apereo.cas.ticket.refreshtoken.OAuth20RefreshTokenFactory
import org.apereo.cas.ticket.registry.TicketRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ScopedProxyMode


@Configuration
@EnableConfigurationProperties(CasConfigurationProperties::class, AlaCasProperties::class)
class OidcConfiguration {

    // override default CAS postLogoutRedirectUrlMatcher bean to allow for custom OIDC logout redirect URL patterns
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Bean
    fun postLogoutRedirectUrlMatcher(alaCasProperties: AlaCasProperties): OidcPostLogoutRedirectUrlMatcher {
        return OidcPostLogoutRedirectUrlMatcher { postLogoutRedirectUrl: String, configuredUrl: String? ->
            alaCasProperties.logout.allowedPatterns.any { it.matcher(postLogoutRedirectUrl).matches() } ||
                    postLogoutRedirectUrl.equals(configuredUrl, ignoreCase = true)
        }
    }

    @Bean("oauthTokenGenerator")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    fun oauthTokenGenerator(
        @Qualifier("defaultDeviceUserCodeFactory") defaultDeviceUserCodeFactory: OAuth20DeviceUserCodeFactory,
        @Qualifier("defaultDeviceTokenFactory") defaultDeviceTokenFactory: OAuth20DeviceTokenFactory,
        @Qualifier("defaultRefreshTokenFactory") defaultRefreshTokenFactory: OAuth20RefreshTokenFactory,
        @Qualifier("defaultAccessTokenFactory") defaultAccessTokenFactory: OAuth20AccessTokenFactory,
        ticketRegistry: TicketRegistry,
        casProperties: CasConfigurationProperties
    ): OAuth20TokenGenerator {
        return OidcDefaultTokenGenerator(
            defaultAccessTokenFactory, defaultDeviceTokenFactory,
            defaultDeviceUserCodeFactory, defaultRefreshTokenFactory, ticketRegistry, casProperties
        )
    }

    @Bean("oauthCasAuthenticationBuilder")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    fun oauthCasAuthenticationBuilder(
        @Qualifier("oauthPrincipalFactory")
        oauthPrincipalFactory: PrincipalFactory,
        @Qualifier("profileScopeToAttributesFilter")
        profileScopeToAttributesFilter: OAuth20ProfileScopeToAttributesFilter,
        @Qualifier(WebApplicationService.BEAN_NAME_FACTORY)
        webApplicationServiceFactory: ServiceFactory<WebApplicationService>,
        requestParameterResolver: OAuth20RequestParameterResolver,
        casProperties: CasConfigurationProperties
    ): OAuth20CasAuthenticationBuilder {
        return OidcDefaultCasAuthenticationBuilder(oauthPrincipalFactory, webApplicationServiceFactory,
            profileScopeToAttributesFilter, requestParameterResolver, casProperties)
    }
}
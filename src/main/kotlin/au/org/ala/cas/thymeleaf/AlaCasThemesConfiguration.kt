package au.org.ala.cas.thymeleaf

import au.org.ala.cas.AlaCasProperties
import au.org.ala.utils.logger
import org.apereo.cas.services.web.CasThymeleafViewResolverConfigurer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.spring5.view.ThymeleafViewResolver

/**
 * Inject all skin properties into all Thymeleaf templates as static variables
 */
@Configuration("alaCasThemesConfiguration")
@EnableConfigurationProperties(AlaCasProperties::class)
class AlaCasThemesConfiguration {

    companion object {
        const val BASE_URL = "baseUrl"
        const val TERMS_URL = "termsUrl"
        const val HEADER_FOOTER_URL = "headerFooterUrl"
        const val FAVION_BASE_URL = "favIconBaseUrl"
        const val BIE_BASE_URL = "bieBaseUrl"
        const val BIE_SEARCH_PATH = "bieSearchPath"
        const val ORG_SHORT_NAME = "orgShortName"
        const val ORG_LONG_NAME = "orgLongName"
        const val ORG_NAME_KEY = "orgNameKey"
        const val USERDETAILS_BASE_URL = "userDetailsUrl"
        const val RESET_PASSWORD_URL = "resetPasswordUrl"
        const val CREATE_ACCOUNT_URL = "createAccountUrl"
        const val ALA_UI_VERSION = "alaUiVersion"
        const val ALA_PROPERTIES = "ala"

        val log = logger()

        @JvmStatic
        fun configureThymeleafViewResolver(thymeleafViewResolver: ThymeleafViewResolver, alaCasProperties: AlaCasProperties) {
            mapOf(
                BASE_URL to alaCasProperties.skin.baseUrl,
                TERMS_URL to alaCasProperties.skin.termsUrl,
                HEADER_FOOTER_URL to alaCasProperties.skin.headerFooterUrl,
                FAVION_BASE_URL to alaCasProperties.skin.favIconBaseUrl,
                BIE_BASE_URL to alaCasProperties.skin.bieBaseUrl,
                BIE_SEARCH_PATH to alaCasProperties.skin.bieSearchPath,
                ORG_SHORT_NAME to alaCasProperties.skin.orgShortName,
                ORG_LONG_NAME to alaCasProperties.skin.orgLongName,
                ORG_NAME_KEY to alaCasProperties.skin.orgNameKey,
                USERDETAILS_BASE_URL to alaCasProperties.skin.userDetailsUrl,
                RESET_PASSWORD_URL to alaCasProperties.skin.resetPasswordUrl,
                CREATE_ACCOUNT_URL to alaCasProperties.skin.createAccountUrl,
                ALA_UI_VERSION to "ala-ui-${alaCasProperties.skin.uiVersion}",
                ALA_PROPERTIES to alaCasProperties
            ).forEach(thymeleafViewResolver::addStaticVariable)
        }
    }

    @Autowired
    lateinit var alaCasProperties: AlaCasProperties

    @Bean
    fun alaThymeleafViewResolverConfigurer() = CasThymeleafViewResolverConfigurer { resolver ->
        log.info("Configuring thymeleaf view resolver with ALA customisations")
        configureThymeleafViewResolver(resolver, alaCasProperties)
    }

}
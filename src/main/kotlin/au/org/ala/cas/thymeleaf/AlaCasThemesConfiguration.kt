package au.org.ala.cas.thymeleaf

import au.org.ala.cas.AlaCasProperties
import org.apereo.cas.services.web.ThemeViewResolver
import org.apereo.cas.services.web.ThemeViewResolverFactory
import org.apereo.cas.services.web.config.CasThemesConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.util.MimeType
import org.thymeleaf.spring4.SpringTemplateEngine
import org.thymeleaf.spring4.view.ThymeleafViewResolver
import java.util.LinkedHashMap

/**
 * Inject all skin properties into all Thymeleaf templates as static variables
 */
@Configuration("alaCasThemesConfiguration")
@EnableConfigurationProperties(AlaCasProperties::class, ThymeleafProperties::class)
open class AlaCasThemesConfiguration : CasThemesConfiguration() {

    companion object {
        const val BASE_URL = "baseUrl"
        const val HEADER_FOOTER_URL = "headerFooterUrl"
        const val FAVION_BASE_URL = "favIconBaseUrl"
        const val BIE_BASE_URL = "bieBaseUrl"
        const val BIE_SEARCH_PATH = "bieSearchPath"
        const val ORG_SHORT_NAME = "orgShortName"
        const val ORG_LONG_NAME = "orgLongName"
        const val ORG_NAME_KEY = "orgNameKey"
        const val USERDETAILS_BASE_URL = "userDetailsUrl"
    }

    @Autowired
    lateinit var alaCasProperties: AlaCasProperties

    @Autowired
    lateinit var thymeleafProperties: ThymeleafProperties

    @Autowired
    lateinit var applicationContext: ApplicationContext

    /**
     * Overrides the themeViewResolverFactory from the real [CasThemesConfiguration] to use
     * our own nonCachingThymeleafViewResolver
     */
    @Bean
    @Qualifier("themeViewResolverFactory")
    override fun themeViewResolverFactory(): ThemeViewResolverFactory {
        val factory = ThemeViewResolver.Factory(nonCachingThymeleafViewResolver(), thymeleafProperties)
        factory.setApplicationContext(applicationContext)
        return factory
    }

    /**
     * Creates a standard CAS [CasThemesConfiguration.nonCachingThymeleafViewResolver] and
     * then augments it with static variables for use in the ALA skin.
     */
    override fun nonCachingThymeleafViewResolver(): ThymeleafViewResolver {
        return super.nonCachingThymeleafViewResolver().apply {
            // ALA static variables
            mapOf(BASE_URL to alaCasProperties.skin.baseUrl,
                    HEADER_FOOTER_URL to alaCasProperties.skin.headerFooterUrl,
                    FAVION_BASE_URL to alaCasProperties.skin.favIconBaseUrl,
                    BIE_BASE_URL to alaCasProperties.skin.bieBaseUrl,
                    BIE_SEARCH_PATH to alaCasProperties.skin.bieSearchPath,
                    ORG_SHORT_NAME to alaCasProperties.skin.orgShortName,
                    ORG_LONG_NAME to alaCasProperties.skin.orgLongName,
                    ORG_NAME_KEY to alaCasProperties.skin.orgNameKey,
                    USERDETAILS_BASE_URL to alaCasProperties.skin.userDetailsUrl
            ).forEach { (k, v) -> addStaticVariable(k, v) }
        }
    }

}
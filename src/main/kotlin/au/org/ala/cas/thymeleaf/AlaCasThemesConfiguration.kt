package au.org.ala.cas.thymeleaf

import au.org.ala.cas.AlaCasProperties
import org.apereo.cas.services.web.config.CasThemesConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
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
@Configuration
@EnableConfigurationProperties(AlaCasProperties::class, ThymeleafProperties::class)
open class AlaStaticThymeleafPropertiesConfiguration {

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
    lateinit var properties: ThymeleafProperties

    @Configuration("casThemesConfiguration")
    @EnableConfigurationProperties(AlaCasProperties::class)
    class AlaCasThemesConfiguration : CasThemesConfiguration() {

        @Autowired
        lateinit var alaCasProperties: AlaCasProperties

        override fun nonCachingThymeleafViewResolver(): ThymeleafViewResolver {
            return super.nonCachingThymeleafViewResolver().apply {
                // ALA static variables
                mapOf(BASE_URL to  alaCasProperties.skin.baseUrl,
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
    // This replicates the Spring Thymeleaf 3 configuration
    // and inserts static variables into the view resolver from the ALA config properties
//    @Bean
//    @ConditionalOnProperty(name = ["spring.thymeleaf.enabled"], matchIfMissing = true)
//    fun thymeleafViewResolver(templateEngine: SpringTemplateEngine): ThymeleafViewResolver {
//        val resolver = ThymeleafViewResolver()
//        resolver.templateEngine = templateEngine
//        resolver.characterEncoding = this.properties.encoding.name()
//        resolver.contentType = appendCharset(this.properties.contentType,
//                resolver.characterEncoding)
//        resolver.excludedViewNames = this.properties.excludedViewNames
//        resolver.viewNames = this.properties.viewNames
//        // This resolver acts as a fallback resolver (e.g. like a
//        // InternalResourceViewResolver) so it needs to have low precedence
//        resolver.order = Ordered.LOWEST_PRECEDENCE - 5
//        resolver.isCache = this.properties.isCache
//
//        // ALA static variables
//        mapOf(BASE_URL to  alaCasProperties.skin.baseUrl,
//                HEADER_FOOTER_URL to alaCasProperties.skin.headerFooterUrl,
//                FAVION_BASE_URL to alaCasProperties.skin.favIconBaseUrl,
//                BIE_BASE_URL to alaCasProperties.skin.bieBaseUrl,
//                BIE_SEARCH_PATH to alaCasProperties.skin.bieSearchPath,
//                ORG_SHORT_NAME to alaCasProperties.skin.orgShortName,
//                ORG_LONG_NAME to alaCasProperties.skin.orgLongName,
//                ORG_NAME_KEY to alaCasProperties.skin.orgNameKey,
//                USERDETAILS_BASE_URL to alaCasProperties.skin.userDetailsUrl
//        ).forEach { (k, v) -> resolver.addStaticVariable(k, v) }
//
//        return resolver
//    }
//
//    private fun appendCharset(type: MimeType, charset: String): String {
//        if (type.charset != null) {
//            return type.toString()
//        }
//        val parameters = LinkedHashMap<String, String>()
//        parameters.put("charset", charset)
//        parameters.putAll(type.parameters)
//        return MimeType(type, parameters).toString()
//    }
}
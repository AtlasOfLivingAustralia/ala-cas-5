package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.utils.logger
import org.apereo.cas.authentication.principal.ServiceFactory
import org.apereo.cas.authentication.principal.WebApplicationService
import org.apereo.cas.authentication.support.password.PasswordEncoderUtils
import org.apereo.cas.configuration.CasConfigurationProperties
import org.apereo.cas.configuration.support.Beans
import org.apereo.cas.services.ServicesManager
import org.apereo.cas.ticket.registry.TicketRegistrySupport
import org.apereo.cas.web.cookie.CasCookieBuilder
import org.apereo.cas.web.cookie.CookieGenerationContext
import org.apereo.cas.web.flow.CasWebflowExecutionPlan
import org.apereo.cas.web.flow.CasWebflowExecutionPlanConfigurer
import org.apereo.cas.web.support.gen.CookieRetrievingCookieGenerator
import org.apereo.services.persondir.IPersonAttributeDao
import org.apereo.services.persondir.support.CachingPersonAttributeDaoImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry
import org.springframework.webflow.engine.builder.support.FlowBuilderServices
import org.springframework.webflow.execution.Action
import javax.sql.DataSource

/**
 * Creates the WebFlow actions required for the changes to the default CAS login/logout WebFlows.
 *
 * Changes to the CAS WebFlows are made through the alaAuthCookieWebflowConfigurer Bean, which is added
 * to the web flow configuration plan by extending CasWebflowExecutionPlanConfigurer
 */
@Configuration("alaCasWebflowConfiguration")
@EnableConfigurationProperties(AlaCasProperties::class, CasConfigurationProperties::class)
class AlaCasWebflowConfiguration : CasWebflowExecutionPlanConfigurer {

    companion object {
        val log = logger()
    }

    @Autowired
    lateinit var alaCasProperties: AlaCasProperties

    @Autowired
    lateinit var casConfigurationProperties: CasConfigurationProperties

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    @Qualifier("loginFlowRegistry")
    lateinit var loginFlowDefinitionRegistry: FlowDefinitionRegistry

    @Autowired
    @Qualifier("logoutFlowRegistry")
    lateinit var logoutFlowDefinitionRegistry: FlowDefinitionRegistry

    @Autowired
    lateinit var flowBuilderServices: FlowBuilderServices

    @Autowired
    @Qualifier("defaultTicketRegistrySupport")
    lateinit var ticketRegistrySupport: TicketRegistrySupport

    @Autowired
    @Qualifier("userCreatorDataSource")
    lateinit var userCreatorDataSource: DataSource

    @Autowired
    lateinit var messageSource: MessageSource

    @Autowired
    @Qualifier("userCreatorTransactionManager")
    lateinit var userCreatorTransactionManager: DataSourceTransactionManager

    @Autowired
    @Qualifier("cachingAttributeRepository")
    lateinit var cachingAttributeRepository: IPersonAttributeDao //CachingPersonAttributeDaoImpl

//    @Autowired
//    @Qualifier("ticketGrantingTicketCookieGenerator")
//    lateinit var ticketGrantingTicketCookieGenerator: CasCookieBuilder
//
//    @Autowired
//    @Qualifier("servicesManager")
//    lateinit var servicesManager: ServicesManager
//
//    @Autowired
//    @Qualifier("webApplicationServiceFactory")
//    lateinit var webApplicationServiceFactory: ServiceFactory<WebApplicationService>

    @Bean
    fun extraAttributesService() = ExtraAttributesService(alaCasProperties, userCreatorDataSource, userCreatorTransactionManager, cachingAttributeRepository, messageSource)

    @Bean
    @RefreshScope
    @Qualifier("alaProxyAuthenticationCookieGenerator")
    fun alaProxyAuthenticationCookieGenerator(): CasCookieBuilder {
        val context = alaCasProperties.cookie.run {
            CookieGenerationContext.builder()
                .name(name)
                .path(path)
                .maxAge(maxAge)
                .secure(isSecure)
                .domain(domain)
                .httpOnly(isHttpOnly)
                .rememberMeMaxAge(Beans.newDuration(rememberMeMaxAge).seconds.toInt())
                .sameSitePolicy(sameSitePolicy)
                .build()
        }
        return CookieRetrievingCookieGenerator(context)
    }

    @Bean
    fun generateAuthCookieAction(): GenerateAuthCookieAction =
        GenerateAuthCookieAction(ticketRegistrySupport, alaProxyAuthenticationCookieGenerator(), alaCasProperties.cookie.quoteValue, alaCasProperties.cookie.urlEncodeValue)

    @Bean
    fun removeAuthCookieAction(): RemoveAuthCookieAction =
        RemoveAuthCookieAction(alaProxyAuthenticationCookieGenerator())

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.ACTION_ENTER_DELEGATED_AUTH_EXTRA_ATTRS)
    fun enterDelegatedAuthAction() : Action = EnterDelegatedAuthAction(alaCasProperties)

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.ACTION_RENDER_DELEGATED_AUTH_EXTRA_ATTRS)
    fun renderDelegatedAuthAction(): Action = RenderDelegatedAuthAction(alaCasProperties, extraAttributesService())

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.ACTION_UPDATE_PASSWORD)
    fun updatePasswordAction(): Action = UpdatePasswordAction(
        alaCasProperties, PasswordEncoderUtils.newPasswordEncoder(alaCasProperties.userCreator.passwordEncoder, applicationContext),
        userCreatorDataSource, userCreatorTransactionManager
    )

//    @RefreshScope
//    @Bean("logoutAction")
//    @Qualifier("logoutAction")
//    fun logoutAction(): Action = AlaLogoutAction(webApplicationServiceFactory, servicesManager, casConfigurationProperties.logout)

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.DECISION_ID_EXTRA_ATTRS)
    fun decisionExtraAttrsAction() = DecisionExtraAttrsAction()

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.STATE_ID_SAVE_EXTRA_ATTRS_ACTION)
    fun saveExtraAttrsAction() = SaveExtraAttrsAction(alaCasProperties, userCreatorDataSource, extraAttributesService())

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.DECISION_ID_SURVEY)
    fun decisionSurveyAction() = DecisionSurveyAction(alaCasProperties, userCreatorDataSource)

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.STATE_ID_SAVE_SURVEY_ACTION)
    fun saveSurveyAction() = SaveSurveyAction(alaCasProperties, userCreatorDataSource, extraAttributesService())

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.ACTION_ENTER_SURVEY)
    fun enterSurveyAction() : Action = EnterSurveyAction(alaCasProperties)

    @Bean
    @Qualifier(AlaCasWebflowConfigurer.ACTION_RENDER_SURVEY)
    fun renderSurveyAction(): Action = RenderSurveyAction(extraAttributesService())

    @ConditionalOnMissingBean(name = ["alaAuthCookieWebflowConfigurer"])
    @Bean
    @DependsOn("defaultWebflowConfigurer", "delegatedAuthenticationWebflowConfigurer")
    fun alaAuthCookieWebflowConfigurer(): AlaCasWebflowConfigurer =
        AlaCasWebflowConfigurer(
            flowBuilderServices,
            loginFlowDefinitionRegistry,
            logoutFlowDefinitionRegistry,
            generateAuthCookieAction(),
            removeAuthCookieAction(),
            applicationContext as ConfigurableApplicationContext,
            alaCasProperties,
            casConfigurationProperties
        )

    override fun configureWebflowExecutionPlan(plan: CasWebflowExecutionPlan) {
        log.debug("Registering alaAuthCookieWebflowConfigurer")
        plan.registerWebflowConfigurer(alaAuthCookieWebflowConfigurer())
    }

}
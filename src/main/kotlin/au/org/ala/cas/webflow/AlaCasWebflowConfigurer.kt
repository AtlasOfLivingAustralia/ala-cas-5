package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.delegated.AccountNotActivatedException
import au.org.ala.utils.logger
import org.apereo.cas.configuration.CasConfigurationProperties
import org.apereo.cas.web.flow.CasWebflowConstants
import org.apereo.cas.web.flow.configurer.AbstractCasWebflowConfigurer
import org.springframework.context.ApplicationContext
import org.springframework.core.Ordered
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry
import org.springframework.webflow.engine.ActionState
import org.springframework.webflow.engine.Flow
import org.springframework.webflow.engine.Transition
import org.springframework.webflow.engine.builder.support.FlowBuilderServices


class AlaCasWebflowConfigurer(
    flowBuilderServices: FlowBuilderServices,
    loginFlowDefinitionRegistry: FlowDefinitionRegistry,
    logoutFlowDefinitionRegistry: FlowDefinitionRegistry,
    val generateAuthCookieAction: GenerateAuthCookieAction,
    val removeAuthCookieAction: RemoveAuthCookieAction,
    applicationContext: ApplicationContext,
    val alaCasProperties: AlaCasProperties,
    casConfigurationProperties: CasConfigurationProperties
) :
    AbstractCasWebflowConfigurer(
        flowBuilderServices,
        loginFlowDefinitionRegistry,
        applicationContext,
        casConfigurationProperties
    ) {

    init {
        this.logoutFlowDefinitionRegistry = logoutFlowDefinitionRegistry
    }

    companion object {
        val log = logger()

        const val STATE_ID_SAVE_EXTRA_ATTRS_ACTION = "saveExtraAttrsAction"
        const val STATE_ID_SAVE_SURVEY_ACTION = "saveSurveyAction"
        const val DECISION_ID_EXTRA_ATTRS = "decisionExtraAttrsAction"
        const val DECISION_ID_SURVEY = "decisionSurveyAction"
        const val VIEW_ID_DELEGATED_AUTH_EXTRA_ATTRS = "delegatedAuthFormView"
        const val VIEW_ID_SURVEY = "userAffiliationSurveyFormView"
        const val VIEW_ID_ACCOUNT_NOT_ACTIVATED = "casAccountNotActivatedView"
        const val ACTION_ENTER_DELEGATED_AUTH_EXTRA_ATTRS = "enterDelegatedAuthAction"
        const val ACTION_ENTER_SURVEY = "enterSurveyAction"
        const val ACTION_RENDER_DELEGATED_AUTH_EXTRA_ATTRS = "renderDelegatedAuthAction"
        const val ACTION_RENDER_SURVEY = "renderSurveyAction"
        const val ACTION_UPDATE_PASSWORD = "updatePasswordAction"
    }

    override fun getOrder() = Ordered.LOWEST_PRECEDENCE

    override fun doInitialize() {
        log.info("doInitialize()")

        val inFlow = loginFlow
        val outFlow = logoutFlow

        if (inFlow != null) {
            doGenerateAuthCookieOnLoginAction(inFlow)
            createViewStates(inFlow)
            addAccountNotActivatedHandler(inFlow)
            // TODO remove once CAS terminateSessionAction in login-flow.xml is fixed.
//            createTerminateSessionAction(inFlow)

            if (alaCasProperties.userCreator.jdbc.enableRequestExtraAttributes) {
                addDelegatedAuthPropertyRequest(inFlow)
            }

            if (alaCasProperties.userCreator.enableUserSurvey) {
                addUserSurveyRequest(inFlow)
            }

            if (alaCasProperties.userCreator.jdbc.enableUpdateLegacyPasswords) {
                addPasswordUpgrade(inFlow)
            }
        }

        if (outFlow != null) {
            doRemoveAuthCookieOnLogoutAction(outFlow)
        }
    }

    private fun addPasswordUpgrade(flow: Flow) {
        // if webflow reaches the CREATE TICKET GRANTING TICKET state that means a user has successfully logged in
        // and because we don't need to affect the flow we can just use an exit action to check for a u/p credential
        // and update the password
        val tgt = flow.getState(CasWebflowConstants.STATE_ID_CREATE_TICKET_GRANTING_TICKET) as ActionState
        tgt.exitActionList.add(createEvaluateAction(ACTION_UPDATE_PASSWORD))
    }

    private fun addDelegatedAuthPropertyRequest(flow: Flow) {
        // Here we want to patch the client action successful delegated login transition to go through
        // our own flow of:
        // 1. Is this is delegated login creating a new user? If Yes continue, otherwise resume existing flow
        // 2. Show extra attributes form to collect additional properties for profile
        // 3.
        val clientAction = flow.getState(CasWebflowConstants.STATE_ID_CLIENT_ACTION) as ActionState
        val successTransition = clientAction.getTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS) as Transition
        createTransitionForState(clientAction, CasWebflowConstants.TRANSITION_ID_SUCCESS, DECISION_ID_EXTRA_ATTRS, true)

        val extraAttrsDecisionAction = createActionState(flow, DECISION_ID_EXTRA_ATTRS, DECISION_ID_EXTRA_ATTRS)
        createTransitionForState(extraAttrsDecisionAction, CasWebflowConstants.TRANSITION_ID_NO, successTransition.targetStateId)
        createTransitionForState(extraAttrsDecisionAction, CasWebflowConstants.TRANSITION_ID_YES, VIEW_ID_DELEGATED_AUTH_EXTRA_ATTRS)
//        createDecisionState(flow, DECISION_ID_EXTRA_ATTRS, "conversationScope.authentication.principal.attributes[${AlaPrincipalFactory.NEW_LOGIN}]", VIEW_ID_DELEGATED_AUTH_EXTRA_ATTRS, successTransition.targetStateId)

//        val delegatedAuthFormAction = createActionState(flow, STATE_ID_DELEGATED_AUTH_FORM_ACTION, STATE_ID_DELEGATED_AUTH_FORM_ACTION)
//        delegatedAuthFormAction.transitionSet.add(successTransition)
//        createTransitionForState(delegatedAuthFormAction, CasWebflowConstants.TRANSITION_ID_NO, VIEW_ID_DELEGATED_AUTH_EXTRA_ATTRS)

//       createFlowVariable(flow, DelegatedAuthFormAction.EXTRA_ATTRS_FLOW_VAR, ExtraAttrs::class.java)

//        val binding = createStateBinderConfiguration(listOf("primaryUserType", "secondaryUserType"))
        val viewState = createViewState(flow, VIEW_ID_DELEGATED_AUTH_EXTRA_ATTRS, "delegatedAuthForm")//, binding)
        viewState.entryActionList.add(createEvaluateAction(ACTION_ENTER_DELEGATED_AUTH_EXTRA_ATTRS))
        viewState.renderActionList.add(createEvaluateAction(ACTION_RENDER_DELEGATED_AUTH_EXTRA_ATTRS))

        createStateModelBinding(viewState, "flowScope.${SaveExtraAttrsAction.EXTRA_ATTRS_FLOW_VAR}", ExtraAttrs::class.java)

        createTransitionForState(viewState, CasWebflowConstants.TRANSITION_ID_SUBMIT, STATE_ID_SAVE_EXTRA_ATTRS_ACTION)

        val saveAction = createActionState(flow, STATE_ID_SAVE_EXTRA_ATTRS_ACTION, STATE_ID_SAVE_EXTRA_ATTRS_ACTION)
        saveAction.transitionSet.add(successTransition)
    }

    private fun addUserSurveyRequest(flow: Flow) {
        // Patch the login flow to ask the user about the survey if they haven't had the survey yet.
        val tgtAction = flow.getState(CasWebflowConstants.STATE_ID_CREATE_TICKET_GRANTING_TICKET) as ActionState
        val successTransition = tgtAction.getTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS) as Transition
        createTransitionForState(tgtAction, CasWebflowConstants.TRANSITION_ID_SUCCESS, DECISION_ID_SURVEY, true)

        val surveyDecision = createActionState(flow, DECISION_ID_SURVEY, DECISION_ID_SURVEY)
        createTransitionForState(surveyDecision, CasWebflowConstants.TRANSITION_ID_NO, successTransition.targetStateId)
        createTransitionForState(surveyDecision, CasWebflowConstants.TRANSITION_ID_YES, VIEW_ID_SURVEY)

        val viewState = createViewState(flow, VIEW_ID_SURVEY, "userAffiliationSurveyForm")
        viewState.entryActionList.add(createEvaluateAction(ACTION_ENTER_SURVEY))
        viewState.renderActionList.add(createEvaluateAction(ACTION_RENDER_SURVEY))

        createStateModelBinding(viewState, "flowScope.${SaveSurveyAction.SURVEY_FLOW_VAR}", Survey::class.java)

        createTransitionForState(viewState, CasWebflowConstants.TRANSITION_ID_SUBMIT, STATE_ID_SAVE_SURVEY_ACTION)

        val saveAction = createActionState(flow, STATE_ID_SAVE_SURVEY_ACTION, STATE_ID_SAVE_SURVEY_ACTION)
        saveAction.transitionSet.add(successTransition)
    }


    private fun doGenerateAuthCookieOnLoginAction(flow: Flow) {
        val generateServiceTicketAction =
            flow.getState(CasWebflowConstants.STATE_ID_GENERATE_SERVICE_TICKET ) as ActionState
        log.debug("doGenerateAuthCookieOnLoginAction {}: {}", generateServiceTicketAction.javaClass.name, generateServiceTicketAction)
        generateServiceTicketAction.exitActionList.add(generateAuthCookieAction)

        val sendTicketGrantingTicketAction =
            flow.getState(CasWebflowConstants.STATE_ID_SEND_TICKET_GRANTING_TICKET) as ActionState
        sendTicketGrantingTicketAction.exitActionList.add(generateAuthCookieAction)
    }

    private fun doRemoveAuthCookieOnLogoutAction(flow: Flow) {
        val doLogoutAction = flow.getState(CasWebflowConstants.STATE_ID_DO_LOGOUT) as ActionState
        log.debug("doRemoveAuthCookieOnLogoutAction {}: {}", doLogoutAction.javaClass.name, doLogoutAction)
        doLogoutAction.entryActionList.add(removeAuthCookieAction)
    }


    private fun createViewStates(flow: Flow) {
        createViewState(flow, VIEW_ID_ACCOUNT_NOT_ACTIVATED, VIEW_ID_ACCOUNT_NOT_ACTIVATED)
    }

    private fun addAccountNotActivatedHandler(flow: Flow) {
        val handler = flow.getState(CasWebflowConstants.STATE_ID_HANDLE_AUTHN_FAILURE) as ActionState
        createTransitionForState(handler, AccountNotActivatedException::class.java.simpleName, VIEW_ID_ACCOUNT_NOT_ACTIVATED)
    }

}
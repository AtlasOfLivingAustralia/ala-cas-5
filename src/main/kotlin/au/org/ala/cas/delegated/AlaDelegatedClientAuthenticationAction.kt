package au.org.ala.cas.delegated

import org.apereo.cas.authentication.AuthenticationException
import org.apereo.cas.pac4j.client.DelegatedClientAuthenticationFailureEvaluator
import org.apereo.cas.web.flow.CasWebflowConstants
import org.apereo.cas.web.flow.DelegatedClientAuthenticationConfigurationContext
import org.apereo.cas.web.flow.DelegatedClientAuthenticationWebflowManager
import org.apereo.cas.web.flow.actions.DelegatedClientAuthenticationAction
import org.springframework.webflow.core.collection.LocalAttributeMap
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext
import javax.security.auth.login.AccountException

/**
 * Patches the CAS DelegatedClientAuthenticationAction to handle AccountExceptions that
 * are thrown from the AlaDelegatedClientAuthenticationCredentialResolver.
 */
class AlaDelegatedClientAuthenticationAction(
    context: DelegatedClientAuthenticationConfigurationContext,
    delegatedClientAuthenticationWebflowManager: DelegatedClientAuthenticationWebflowManager,
    failureEvaluator: DelegatedClientAuthenticationFailureEvaluator
) : DelegatedClientAuthenticationAction(context, delegatedClientAuthenticationWebflowManager, failureEvaluator) {

    override fun stopWebflow(e: Exception, requestContext: RequestContext): Event {
        // Wrap AccountException in AuthenticationException to work with existing DefaultCasWeblflowAuthenticationExceptionHandler
        if (e is AccountException) {
            val wrapper = AuthenticationException(mapOf("DelegatedClientAuthenticationAction" to e))
            requestContext.flashScope.put(CasWebflowConstants.ATTRIBUTE_ERROR_ROOT_CAUSE_EXCEPTION, wrapper)
            return Event(this, CasWebflowConstants.TRANSITION_ID_AUTHENTICATION_FAILURE, LocalAttributeMap(CasWebflowConstants.TRANSITION_ID_ERROR, wrapper))
        } else if (e is AuthenticationException) {
            requestContext.flashScope.put(CasWebflowConstants.ATTRIBUTE_ERROR_ROOT_CAUSE_EXCEPTION, e)
            return Event(this, CasWebflowConstants.TRANSITION_ID_AUTHENTICATION_FAILURE, LocalAttributeMap(CasWebflowConstants.TRANSITION_ID_ERROR, e))
        }
        return super.stopWebflow(e, requestContext)
    }
}
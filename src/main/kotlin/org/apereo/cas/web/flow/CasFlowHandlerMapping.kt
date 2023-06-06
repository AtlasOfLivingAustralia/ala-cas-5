package org.apereo.cas.web.flow

import org.springframework.webflow.mvc.servlet.FlowHandlerMapping

/**
 * Backport this fix from CAS 7: https://github.com/apereo/cas/pull/5634
 *
 * @author Misagh Moayyed
 * @since 6.5.0
 */
class CasFlowHandlerMapping : FlowHandlerMapping()
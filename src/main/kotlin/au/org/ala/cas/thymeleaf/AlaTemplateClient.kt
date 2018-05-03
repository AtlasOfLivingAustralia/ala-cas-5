package au.org.ala.cas.thymeleaf

import au.org.ala.cas.SkinProperties
import au.org.ala.utils.logger
import au.org.ala.utils.readText
import com.github.benmanes.caffeine.cache.Caffeine
import org.apereo.cas.configuration.support.Beans
import org.apereo.cas.ticket.registry.TicketRegistry
import org.apereo.cas.util.HttpRequestUtils
import org.apereo.cas.web.support.CookieRetrievingCookieGenerator
import org.apereo.cas.web.support.WebUtils
import org.springframework.webflow.execution.RequestContext
import org.springframework.webflow.execution.RequestContextHolder
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest

class AlaTemplateClient(
    val skinConfig: SkinProperties,
//    val cookieName: String,
    val ticketGrantingTicketCookieGenerator: CookieRetrievingCookieGenerator,
    val ticketRegistry: TicketRegistry) {

    companion object {
        const val LOGGED_IN_CLASS = "logged-in"
        const val LOGGED_OUT_CLASS = "not-logged-in"

        val log = logger()
    }

    val uri = URI(skinConfig.headerFooterUrl)
    val cache = Caffeine.newBuilder().expireAfterWrite(Beans.newDuration(skinConfig.cacheDuration).toMillis(), TimeUnit.MILLISECONDS)
        .build(this::loadTemplate)

    fun loadTemplate(template: String) =
        uri.resolve("./$template.html").also { log.debug("Loading template from {}", it) }.readText()

    fun load(name: String, request: HttpServletRequest?, fluidLayout: Boolean = false): String? {
        val cached = try {
            cache[name]
        } catch (e: Exception) {
            log.error("Couldn't load {}", name, e)
            null
        }

        if (cached == null || cached.isBlank()) {
            log.error("Got a blank cached template value: {}", cached)
            return null
        }
        val loggedIn = isLoggedIn()
        var content = cached.replace("::headerFooterServer::", skinConfig.headerFooterUrl)
            .replace("::centralServer::", skinConfig.baseUrl)
            .replace("::searchServer::", skinConfig.bieBaseUrl)
            .replace("::searchPath::", skinConfig.bieSearchPath)
            .replace("::authStatusClass::", if (loggedIn) LOGGED_IN_CLASS else LOGGED_OUT_CLASS)
        if (fluidLayout) {
            content = content.replace("class=\"container\"", "class=\"container-fluid\"")
        }
        if (content.contains("::loginLogoutListItem::")) {
            // only do the work if it is needed
            content = content.replace("::loginLogoutListItem::", buildLoginoutLink(request, loggedIn))
        }
        return content
    }

    fun isLoggedIn(): Boolean {
        val request: HttpServletRequest? = HttpRequestUtils.getHttpServletRequestFromRequestAttributes()
        val requestContext: RequestContext? = RequestContextHolder.getRequestContext()
        val cookieTgt = request?.let { ticketGrantingTicketCookieGenerator.retrieveCookieValue(request) }
        val requestTgt = requestContext?.let { WebUtils.getTicketGrantingTicketId(requestContext) }
        val tgt = cookieTgt ?: requestTgt
        val validTicket = tgt?.let { ticketRegistry.getTicket(tgt)?.isExpired?.not() }
        return if (validTicket != null) {
            validTicket
        } else {
            val credential = requestContext?.let { WebUtils.getCredential(it) != null }
//            val pac4j = (Pac4jUtils.getPac4jAuthenticatedUsername() != PrincipalResolver.UNKNOWN_USER)
            credential ?: false
        }
    }

    /**
     * Builds the login or logout link based on current login status.
     * @param attrs any specified params to override defaults
     * @return
     */
    fun buildLoginoutLink(request: HttpServletRequest?, loggedIn: Boolean): String {

        return if (loggedIn) {
            val casLogoutUrl = request?.servletContext?.contextPath + "/logout"
            "<a href=\"$casLogoutUrl\">Logout</a>"
        } else {
            // currently logged out
            val casLoginUrl = request?.servletContext?.contextPath + "/login"
            "<a href=\"$casLoginUrl\">Log in</a>"
        }
    }
}
package au.org.ala.cas.thymeleaf

import au.org.ala.cas.SkinProperties
import au.org.ala.utils.logger
import com.github.benmanes.caffeine.cache.Caffeine
import org.apereo.cas.util.Pac4jUtils
import org.apereo.cas.web.support.WebUtils
import org.apereo.inspektr.common.spi.PrincipalResolver
import org.springframework.webflow.execution.RequestContextHolder
import java.io.Reader
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest

class AlaTemplateClient(val skinConfig: SkinProperties, val cookieName: String) {

    companion object {
        const val LOGGED_IN_CLASS = "logged-in"
        const val LOGGED_OUT_CLASS = "not-logged-in"

        val log = logger<AlaHeaderFooterTagProcessor>()
    }

    val uri = URI(skinConfig.headerFooterUrl)
    val cache = Caffeine.newBuilder().expireAfterWrite(skinConfig.cacheDuration, TimeUnit.MILLISECONDS).build(this::loadTemplate)

    fun loadTemplate(template: String) = uri.resolve("./$template.html").toURL().openStream().reader().use(Reader::readText)

    fun load(name: String, request: HttpServletRequest?, fluidLayout: Boolean = false): String? {
        val cached = try {
            cache[name]
        } catch (e: Exception) {
            log.error("Couldn't load {}", name, e)
            null
        }

        if (cached == null || cached.isBlank()) {
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

    fun isLoggedIn() =
            RequestContextHolder.getRequestContext()?.let { WebUtils.getCredential(it) != null } ?: (Pac4jUtils.getPac4jAuthenticatedUsername() != PrincipalResolver.UNKNOWN_USER)


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
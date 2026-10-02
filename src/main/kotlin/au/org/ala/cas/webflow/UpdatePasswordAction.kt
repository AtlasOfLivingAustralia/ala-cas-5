package au.org.ala.cas.webflow

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.alaUserId
import au.org.ala.cas.booleanAttribute
import au.org.ala.cas.jdbc.AlaUserJdbcService
import au.org.ala.utils.logger
import org.apereo.cas.authentication.credential.UsernamePasswordCredential
import org.apereo.cas.web.support.WebUtils
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.webflow.action.AbstractAction
import org.springframework.webflow.execution.Event
import org.springframework.webflow.execution.RequestContext

class UpdatePasswordAction(
    val alaCasProperties: AlaCasProperties,
    val passwordEncoder: PasswordEncoder,
    val alaUserJdbcService: AlaUserJdbcService
) : AbstractAction() {

    companion object {
        val log = logger()
    }

    override fun doExecute(context: RequestContext): Event {
        val credential = WebUtils.getCredential(context)
        val authentication = WebUtils.getAuthentication(context)
        val userid = authentication.alaUserId()
        val legacyPassword = authentication.booleanAttribute("legacyPassword") ?: false
        if (credential != null && credential is UsernamePasswordCredential && !credential.password.isNullOrBlank() && legacyPassword && userid != null) {
            log.info("Upgrading legacy password for {} ({})", credential.username, userid)
            try {
                alaUserJdbcService.updateLegacyPassword(userid, passwordEncoder.encode(credential.password))
            } catch (e: Exception) {
                log.warn("Couldn't update password for {} ({})", credential.username, userid, e)
            }
        }
        return success()
    }
}

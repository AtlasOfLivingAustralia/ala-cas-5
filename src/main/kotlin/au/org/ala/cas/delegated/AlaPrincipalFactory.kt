package au.org.ala.cas.delegated

import au.org.ala.utils.logger
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.apereo.cas.authentication.Credential
import org.apereo.cas.authentication.principal.Principal
import org.apereo.cas.authentication.principal.PrincipalFactory
import org.apereo.cas.authentication.principal.PrincipalResolver
import javax.security.auth.login.FailedLoginException

class AlaPrincipalFactory(private val principalResolver: PrincipalResolver,
                          val userCreator: UserCreator): PrincipalFactory {

    companion object {
        private const val serialVersionUID: Long = -3999695695604948495L
        private val logger = logger<AlaPrincipalFactory>()
        val EMAIL_PATTERN = Regex("^.+@.+\\..+$")
    }

    override fun createPrincipal(id: String) = createAlaPrincipal(id, emptyMap())

    override fun createPrincipal(id: String, attributes: Map<String, Any>) = createAlaPrincipal(id, attributes)

    private fun createAlaPrincipal(id: String, attributes: Map<String, Any>): Principal {
        val attributeParser = AttributeParser.create(id, attributes)
        val email = attributeParser.findEmail()
        logger.debug("email : {}", email)

        if (email == null || !EMAIL_PATTERN.matches(email)) {
            logger.info("ID {} provided an invalid email address: {}, authentication aborted!", id, email)
            logger.debug("ID {} params: {}", id, attributes)
            throw FailedLoginException("No email address found in $email; email address is required to lookup (and/or create) ALA user!")
        }

        //NOTE: just in case social media gave us email containing Upper case letters
        val emailAddress = email.toLowerCase()
        val alaCredential = Credential { emailAddress } // SAM conversion

        // get the ALA user attributes from the userdetails DB ("userid", "firstname", "lastname", "authority")
        var principal = principalResolver.resolve(alaCredential)
        logger.debug("{} resolved principal: {}", principalResolver, principal)

        // does the ALA user exist?
        if (!validatePrincipalALA(principal)) {
            // create a new ALA user in the userdetails DB
            logger.debug("user {} not found in ALA userdetails DB, creating new ALA user for: {}.", emailAddress, emailAddress)

            val firstName = attributeParser.findFirstname()
            val lastName = attributeParser.findLastname()

            if (firstName != null && lastName != null)  {
                // if no userId parameter is returned then no db entry was created
                val userId = userCreator.createUser(emailAddress, firstName, lastName) ?: throw FailedLoginException("Unable to create user for $emailAddress, $firstName, $lastName")
                logger.debug("Received new user id {}", userId)

                // re-try (we have to retry, because that is how we get the required "userid")
                principal = principalResolver.resolve(alaCredential)

                logger.debug("{} resolved principal: {}", principalResolver, principal)
            } else {
                logger.warn("Couldn't extract firstname or lastname for {} from attributes: {}", id, attributes)
            }

            if (!validatePrincipalALA(principal)) {
                // we failed to lookup ALA user (most likely because the creation above failed), complain, throw exception, etc.
                throw FailedLoginException("Unable to create ALA user for $emailAddress with attributes $attributes")
            }
        }
        return principal
    }

    internal fun validatePrincipalALA(principal: Principal?) = principal != null && principal.attributes != null && principal.attributes.containsKey("userid")


    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other === this) {
            return true
        }
        return other.javaClass == javaClass
    }

    override fun hashCode(): Int {
        return HashCodeBuilder(13, 37).toHashCode()
    }
}

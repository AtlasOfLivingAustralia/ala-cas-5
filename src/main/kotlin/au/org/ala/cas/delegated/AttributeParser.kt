package au.org.ala.cas.delegated

import au.org.ala.cas.singleBooleanAttributeValue
import au.org.ala.cas.singleStringAttributeValue
import au.org.ala.utils.logger
import org.json.JSONArray
import org.pac4j.core.profile.UserProfile
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.oauth.profile.facebook.FacebookProfile
import org.pac4j.oauth.profile.github.GitHubProfile
import org.pac4j.oauth.profile.google2.Google2Profile
import org.pac4j.oauth.profile.google2.Google2ProfileDefinition
import org.pac4j.oauth.profile.linkedin2.LinkedIn2Profile
import org.pac4j.oauth.profile.twitter.TwitterProfile
import org.pac4j.oauth.profile.windowslive.WindowsLiveProfile
import org.pac4j.oidc.profile.OidcProfile
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

interface AttributeParser {

    companion object {
        /** Log instance.  */
        private val log = logger()

        fun create(profile: UserProfile): AttributeParser {
            return when (profile.javaClass) {
                GitHubProfile::class.java -> GithubAttributeParser(profile.attributes)
                FacebookProfile::class.java,
                LinkedIn2Profile::class.java,
                WindowsLiveProfile::class.java,
                OidcProfile::class.java -> OAuth20AttributeParser(profile.attributes)
                TwitterProfile::class.java -> TwitterAttributeParser(profile.attributes)
                Google2Profile::class.java -> Google2AttributeParser(profile.attributes)
                else -> throw IllegalArgumentException("Unsupported profile type: ${profile.javaClass.name}").also { log.error("Aborting due to unsupported profile type: ${profile.javaClass.name}") }
            }
        }

        fun create(typedId: String, userAttributes: Map<String, Any>): AttributeParser {
            val profileType = typedId.substringBefore(Pac4jConstants.TYPED_ID_SEPARATOR)

            return when (profileType) {
                GitHubProfile::class.java.name -> GithubAttributeParser(userAttributes)
                FacebookProfile::class.java.name,
                LinkedIn2Profile::class.java.name,
                WindowsLiveProfile::class.java.name,
                OidcProfile::class.java.name -> OAuth20AttributeParser(userAttributes)
                TwitterProfile::class.java.name -> TwitterAttributeParser(userAttributes)
                Google2Profile::class.java.name -> Google2AttributeParser(userAttributes)
                else -> throw IllegalArgumentException("Unsupported profile type: $typedId").also { log.error("Aborting due to unsupported profile type in typed id: $typedId") }
            }
        }

        private val whiteSpace = Regex("\\s")

        internal fun extractFirstName(name: String?, defaultValue: String?): String? {
            log.debug("getting firstname from name: {}", name)

            if (name == null || name.isEmpty()) {
                return defaultValue
            }

            val nameString = name.split(whiteSpace).dropLastWhile(String::isEmpty).firstOrNull()
            return nameString
        }

        internal fun extractLastName(name: String?, defaultValue: String?): String? {
            log.debug("getting lastname from name: {}", name)

            if (name == null || name.isEmpty()) {
                return defaultValue
            }

            val nameStrings = name.split(whiteSpace).dropLastWhile(String::isEmpty)
            log.debug("nameStrings: {}, nameStrings.length: {}", nameStrings, nameStrings.size)

            return if (nameStrings.size == 1) {
                nameStrings.firstOrNull()
            } else {
                nameStrings.drop(1).joinToString(" ")

            }
        }
    }

    fun findEmail(): String?
    fun findFirstname(): String?
    fun findLastname(): String?
}

class GithubAttributeParser(val userAttributes: Map<String, Any>) : AttributeParser {

    companion object {
        val log = logger()
    }

    override fun findEmail(): String? {
        //       WARNING: as of today (2015-06-10) GitHub is allowing to set/configure an UNVERIFIED email address in
        //       the Public profile -> Public email; this seems to allow for (at least 2 problematic scenarios):
        //       1. abuse ALA with unverified GitHub emails, BUT the next one is much scarier:
        //       2. a GitHub user (attacker), say myself (mbohun/martin.bohun@gmail.com) can add to his GitHub profile Emails
        //          victim's email address, then set the victim's email address in Public profile -> Public email. After this
        //          the attacker can login into ALA via "Login with GitHub" and the attacker is logged into ALA as the victim.
        //          The only limitation/restriction is if the (ALA) victim has an existing GitHub profile registered/using
        //          the victim's email address (the same email address the victim uses for ALA); in such scenario GitHub won't
        //          allow the attacker to use/add an email address that is alrady used by another GitHub user (the victim).
        //          This leads to the conclusion that the safest solution is to ALWAYS ignore the GitHub profile email address,
        //          use the access_token to REST HTTP GET https://api.github.com/user/emails?access_token=${access_token} the
        //          array/set of GitHub user's emails, and use the email address that is: primary AND verified.
        //

        val githubAccessToken = singleStringAttributeValue(userAttributes["access_token"])
        if (githubAccessToken == null) {
            log.debug("can't get a valid GitHub access_token!")
            return null
        }

        val githubEmailREST = "https://api.github.com/user/emails"

        val result = HTTP_GET(githubEmailREST, githubAccessToken)
        log.debug("HTTP_GET {}; result: {}", githubEmailREST, result)

        try {

            val emails = JSONArray(result)
            log.debug("GitHub emails: {}", emails)

            for (i in 0 until emails.length()) {
                val emailRecord = emails.getJSONObject(i)
                if (emailRecord.getBoolean("primary")) { //TODO: enforce verified email: && emailRecord.getBoolean("verified")
                    val email = emailRecord.getString("email")
                    log.debug("using GitHub email: {}", email)
                    return email
                }
            }
            return null
        } catch (e: Throwable) {
            log.warn("error parsing github JSON response", e)
            return null
        }

        // we did NOT find an email? not sure how likely is that, maybe later: we did NOT find any VERIFIED email
    }

    override fun findFirstname() =
        AttributeParser.extractFirstName(singleStringAttributeValue(userAttributes["name"]), singleStringAttributeValue(userAttributes["login"]))

    override fun findLastname() =
        AttributeParser.extractLastName(singleStringAttributeValue(userAttributes["name"]), singleStringAttributeValue(userAttributes["login"]))

    fun HTTP_GET(urlStr: String, githubAccessToken: String): String? {
        var conn: HttpURLConnection? = null
        var reader: BufferedReader? = null

        try {

            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            val authHeader = "token $githubAccessToken"
            conn.setRequestProperty("Authorization", authHeader)

            reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)) // assume utf-8

            return reader.readText()

        } catch (e: Exception) {
            log.warn("HTTP_GET error for {}", urlStr, e)

        } finally {
            try {
                reader?.close()
                conn?.disconnect()

            } catch (ioe: IOException) {
                log.warn("Exception while closing HTTP_GET {}", urlStr, ioe)
            }
        }
        return null
    }
}

class OAuth20AttributeParser(val userAttributes: Map<String, Any>) : AttributeParser {
    override fun findEmail() = findFirst("email", "email-address")
    override fun findFirstname() = findFirst("first_name", "first-name", "given_name", "given-name")
    override fun findLastname() = findFirst("last_name", "last-name", "family_name", "family-name")

    internal fun findFirst(vararg attributeNames: String) =
        attributeNames.mapNotNull { singleStringAttributeValue(userAttributes[it]) }.firstOrNull()
}

class TwitterAttributeParser(val userAttributes: Map<String, Any>) : AttributeParser {
    override fun findEmail() = singleStringAttributeValue(userAttributes["email"])
    override fun findFirstname() =
        AttributeParser.extractFirstName(singleStringAttributeValue(userAttributes["name"]), singleStringAttributeValue(userAttributes["screen_name"]))

    override fun findLastname() =
        AttributeParser.extractLastName(singleStringAttributeValue(userAttributes["name"]), singleStringAttributeValue(userAttributes["screen_name"]))
}

class Google2AttributeParser(val userAttributes: Map<String, Any>) : AttributeParser {
    companion object {
        val log = logger()
    }

    override fun findEmail(): String? {
        // NOTE: in theory we could do here: return Google2Profile.getEmail() BUT that seems
        //       to be grabbing and returning the first email from the list without checking
        //       the email "type"; so just to be sure we do ENFORCE here returning the email
        //       of type "account".
        //
//        val emails = userAttributes[Google2ProfileDefinition.EMAILS]
        val googleEmail =
                if (singleBooleanAttributeValue(userAttributes[Google2ProfileDefinition.EMAIL_VERIFIED]) == true) {
                    singleStringAttributeValue(userAttributes[Google2ProfileDefinition.EMAIL])
                } else {
                    null
                }

        // Not sure if this can really happen; we should reach this point only after a successful authentication,
        // and that should be possible ONLY with a valid "account" email.
        if (googleEmail == null) {
            log.debug("error, can't find required Google2Profile email of type \"account\" in {}!", userAttributes)
        }
        return googleEmail
    }

    override fun findFirstname() =
        singleStringAttributeValue(userAttributes[Google2ProfileDefinition.GIVEN_NAME])
            ?: AttributeParser.extractFirstName(singleStringAttributeValue(userAttributes[Google2ProfileDefinition.NAME]), "")

    override fun findLastname() =
        singleStringAttributeValue(userAttributes[Google2ProfileDefinition.FAMILY_NAME])
            ?: singleStringAttributeValue(userAttributes["family_name"])
            ?: AttributeParser.extractLastName(singleStringAttributeValue(userAttributes[Google2ProfileDefinition.NAME]), "")

}

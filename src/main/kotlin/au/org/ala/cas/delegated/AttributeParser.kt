package au.org.ala.cas.delegated

import au.org.ala.cas.booleanAttribute
import au.org.ala.cas.stringAttribute
import au.org.ala.utils.logger
import au.org.ala.utils.urlParameterSafe
import org.json.JSONArray
import org.pac4j.core.profile.UserProfile
import org.pac4j.oauth.profile.facebook.FacebookProfile
import org.pac4j.oauth.profile.github.GitHubProfile
import org.pac4j.oauth.profile.google2.Google2Email
import org.pac4j.oauth.profile.google2.Google2Profile
import org.pac4j.oauth.profile.google2.Google2ProfileDefinition
import org.pac4j.oauth.profile.linkedin2.LinkedIn2Profile
import org.pac4j.oauth.profile.twitter.TwitterProfile
import org.pac4j.oauth.profile.windowslive.WindowsLiveProfile
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

interface AttributeParser {

    companion object {
        /** Log instance.  */
        private val log = logger()

        fun create(typedId: String, userAttributes: Map<String, List<Any>>): AttributeParser {
            val profileType = typedId.substringBefore(UserProfile.SEPARATOR)

            return when (profileType) {
                GitHubProfile::class.java.name -> GithubAttributeParser(userAttributes)
                FacebookProfile::class.java.name,
                LinkedIn2Profile::class.java.name,
                WindowsLiveProfile::class.java.name -> OAuth20AttributeParser(userAttributes)
                TwitterProfile::class.java.name -> TwitterAttributeParser(userAttributes)
                Google2Profile::class.java.name -> Google2AttributeParser(userAttributes)
                else -> throw IllegalArgumentException("Unsupported profile type: $typedId")
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

class GithubAttributeParser(val userAttributes: Map<String, List<Any>>) : AttributeParser {

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

        val githubAccessToken = userAttributes["access_token"]
        if (githubAccessToken == null || githubAccessToken.isEmpty()) {
            log.debug("can't get a valid GitHub access_token!")
            return null
        }

        val githubEmailREST = "https://api.github.com/user/emails?access_token=${githubAccessToken.first().toString().urlParameterSafe()}"

        val result = httpGet(githubEmailREST)
        log.debug("httpGet {}; result: {}", githubEmailREST, result)

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
        AttributeParser.extractFirstName(userAttributes.stringAttribute("name"), userAttributes.stringAttribute("login"))

    override fun findLastname() =
        AttributeParser.extractLastName(userAttributes.stringAttribute("name"), userAttributes.stringAttribute("login"))

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        var reader: BufferedReader? = null

        try {

            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"

            reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)) // assume utf-8

            return reader.readText()

        } catch (e: Exception) {
            log.warn("httpGet error for {}", urlStr, e)

        } finally {
            try {
                reader?.close()
                conn?.disconnect()

            } catch (ioe: IOException) {
                log.warn("Exception while closing httpGet {}", urlStr, ioe)
            }
        }
        return null
    }
}

class OAuth20AttributeParser(val userAttributes: Map<String, List<Any>>) : AttributeParser {
    override fun findEmail() = findFirst("email", "email-address")
    override fun findFirstname() = findFirst("first_name", "first-name")
    override fun findLastname() = findFirst("last_name", "last-name")

    internal fun findFirst(vararg attributeNames: String) =
        attributeNames.mapNotNull { userAttributes.stringAttribute(it) }.firstOrNull()
}

class TwitterAttributeParser(val userAttributes: Map<String, List<Any>>) : AttributeParser {
    override fun findEmail() = userAttributes.stringAttribute("email")
    override fun findFirstname() =
        AttributeParser.extractFirstName(userAttributes.stringAttribute("name"), userAttributes.stringAttribute("screen_name"))

    override fun findLastname() =
        AttributeParser.extractLastName(userAttributes.stringAttribute("name"), userAttributes.stringAttribute("screen_name"))
}

class Google2AttributeParser(val userAttributes: Map<String, List<Any>>) : AttributeParser {
    companion object {
        val log = logger()
    }

    override fun findEmail(): String? {
        // NOTE: in theory we could do here: return Google2Profile.getEmail() BUT that seems
        //       to be grabbing and returning the first email from the list without checking
        //       the email "type"; so just to be sure we do ENFORCE here returning the email
        //       of type "account".
        //
        val emails = userAttributes[Google2ProfileDefinition.EMAILS]

        val googleEmail = if (emails.isNullOrEmpty()) {
            val verified = userAttributes.booleanAttribute(Google2ProfileDefinition.EMAIL_VERIFIED)
            if (verified != false) {
                userAttributes.stringAttribute(Google2ProfileDefinition.EMAIL)
            } else {
                null
            }
        } else {
            emails.mapNotNull { candidate ->
                when (candidate) {
                    is Google2Email -> if (candidate.type == "account") candidate.email else null
                    is String -> candidate
                    else -> null
                }
            }.firstOrNull()
        }

        // Not sure if this can really happen; we should reach this point only after a successful authentication,
        // and that should be possible ONLY with a valid "account" email.
        if (googleEmail == null) {
            log.debug("error, can't find required Google2Profile email of type \"account\" in {}!", emails)
        }
        return googleEmail
    }

    override fun findFirstname() =
            userAttributes.stringAttribute(Google2ProfileDefinition.GIVEN_NAME) ?:
            AttributeParser.extractFirstName(userAttributes.stringAttribute(Google2ProfileDefinition.NAME), "")

    override fun findLastname() =
            userAttributes.stringAttribute(Google2ProfileDefinition.FAMILY_NAME) ?:
            userAttributes.stringAttribute("family_name") ?:
            AttributeParser.extractLastName(userAttributes.stringAttribute(Google2ProfileDefinition.NAME), "")

}
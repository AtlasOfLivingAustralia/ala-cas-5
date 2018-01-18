package au.org.ala.cas.password

import au.org.ala.utils.logger
import com.google.common.hash.Hashing
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.*

/**
 * PasswordEncoder that implements that salted MD5 algorithm used in legacy ALA CAS installs.
 *
 * It's now used as fallback for matching legacy passwords.
 */
class AlaLegacyEncoder(val salt: String, val base64Encode: Boolean = true) : PasswordEncoder {

    companion object {
        private val log = logger<AlaLegacyEncoder>()
        val OLD_ALA_PATTERN = Regex("^[a-zA-Z0-9+/]{22,24}={0,2}$")
    }

    override fun encode(rawPassword: CharSequence): String {
        val salted = "$rawPassword{$salt}"
        val hash = Hashing.md5().hashBytes(salted.toByteArray(Charsets.UTF_8)).asBytes()
        return if(base64Encode) Base64.getEncoder().encodeToString(hash) else String(hash)
    }

    override fun matches(rawPassword: CharSequence, encodedPassword: String?): Boolean {

        if (encodedPassword == null || encodedPassword.isEmpty()) {
            log.warn("Empty encoded password")
            return false
        }

        if (base64Encode && !OLD_ALA_PATTERN.matches(encodedPassword)) {
            log.debug("Encoded password does not look like base64 encoded salted MD5")
            return false
        }

        return equalsNoEarlyReturn(encodedPassword, encode(rawPassword))
    }

    internal fun equalsNoEarlyReturn(a: String, b: String): Boolean {
        val caa = a.toCharArray()
        val cab = b.toCharArray()

        if (caa.size != cab.size) {
            return false
        }

        var ret = true
        for (i in caa.indices) {
            ret = ret and (caa[i] == cab[i])
        }
        return ret
    }
}
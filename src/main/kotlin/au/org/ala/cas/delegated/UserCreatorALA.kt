package au.org.ala.cas.delegated

import au.org.ala.utils.logger
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.simple.SimpleJdbcCall
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import javax.sql.DataSource

class UserCreatorALA(
    val dataSource: DataSource,
    val createUserProcedure: String,
    val userCreatePassword: String,
    val passwordEncoder: PasswordEncoder = NoOpPasswordEncoder.getInstance()
) : UserCreator {

    companion object {
        /** Log instance.  */
        private val log = logger()
    }

    private val jdbcTemplate = JdbcTemplate(dataSource)

    override fun createUser(email: String, firstName: String, lastName: String): Long? {
        log.debug("createUser: {} {} {}", email, firstName, lastName)

        val password = passwordEncoder.encode(userCreatePassword)

        val call = SimpleJdbcCall(jdbcTemplate).apply {
            procedureName = createUserProcedure
        }

        val inParams = MapSqlParameterSource()
            .addValue("email", email.toLowerCase())
            .addValue("firstname", firstName)
            .addValue("lastname", lastName)
            .addValue("password", password)
            .addValue("organisation", "")
            .addValue("city", "")
            .addValue("state", "")
            .addValue("country", "")
//            .addValue("primaryUserType", "")
//            .addValue("secondaryUserType", "")
//            .addValue("telephone", "")

        val result = call.execute(inParams)

        val userId = result["user_id"]

        log.debug("createUser created user id: {}", userId)

        return userId as? Long ?: (userId as? Int)?.toLong() ?: null.also {
            log.warn("Couldn't extract userId from {}", result)
        }
    }

}
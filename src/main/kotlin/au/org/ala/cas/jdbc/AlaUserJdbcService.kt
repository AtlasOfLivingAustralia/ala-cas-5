package au.org.ala.cas.jdbc

import au.org.ala.cas.AlaCasProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

class AlaUserJdbcService(
    private val alaCasProperties: AlaCasProperties,
    dataSource: DataSource,
    transactionManager: PlatformTransactionManager
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private val namedJdbcTemplate = NamedParameterJdbcTemplate(dataSource)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun updateLastLogin(userid: Long): Int = jdbcTemplate.update(alaCasProperties.userCreator.jdbc.updateLastLoginTimeSql, userid)

    fun countProfile(userid: Long, name: String): Int {
        return namedJdbcTemplate.queryForObject(
            alaCasProperties.userCreator.jdbc.countExtraAttributeSql,
            mapOf("userid" to userid, "name" to name),
            Int::class.java
        ) ?: 0
    }

    fun shouldOfferSurvey(userid: Long): Boolean = countProfile(userid, "affiliation") == 0

    fun upsertProfile(userid: Long, name: String, value: String): Int {
        val params = mapOf("userid" to userid, "name" to name, "value" to value)
        return if (countProfile(userid, name) > 0) {
            namedJdbcTemplate.update(alaCasProperties.userCreator.jdbc.updateExtraAttributeSql, params)
        } else {
            namedJdbcTemplate.update(alaCasProperties.userCreator.jdbc.insertExtraAttributeSql, params)
        }
    }

    fun updateLegacyPassword(userid: Long, encodedPassword: String) {
        val params = mapOf("userid" to userid, "password" to encodedPassword)
        transactionTemplate.execute { status ->
            try {
                alaCasProperties.userCreator.jdbc.updatePasswordSqls.forEach { sql ->
                    namedJdbcTemplate.update(sql, params)
                }
            } catch (e: Exception) {
                status.setRollbackOnly()
                throw e
            }
        }
    }
}

package au.org.ala.cas.mysql

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.delegated.UserCreatorALA
import au.org.ala.cas.jdbc.AlaUserJdbcService
import com.mysql.cj.jdbc.MysqlDataSource
import org.flywaydb.core.Flyway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

abstract class AbstractMysqlSqlIntegration(
    private val mysqlVersion: String,
    private val imageName: String
) {

    companion object {
        private const val BCRYPT_AUTH_SQL = "SELECT p.password as password, not u.activated or u.locked as disabled, COALESCE(p.expiry < CURRENT_TIMESTAMP(), 0) as expired, FALSE as legacy_password FROM passwords p JOIN users u ON u.userid = p.userid AND p.status = 'CURRENT' AND p.type = 'bcrypt' WHERE u.username = ?;"
        private const val LEGACY_AUTH_SQL = "SELECT p.password as password, not u.activated or u.locked as disabled, COALESCE(p.expiry < CURRENT_TIMESTAMP(), 0) as expired, TRUE as legacy_password FROM passwords p JOIN users u ON u.userid = p.userid AND p.status = 'CURRENT' AND p.type <> 'bcrypt' WHERE u.username = ?;"
    }

    @Test
    fun `CAS SQL paths work against MySQL`() {
        withMysql { dataSource, local ->
            val jdbc = JdbcTemplate(dataSource)
            val properties = AlaCasProperties()
            val userJdbcService = AlaUserJdbcService(properties, dataSource, DataSourceTransactionManager(dataSource))

            assertVersion(jdbc, local)
            assertEquals(1, jdbc.queryForObject("/* ping */ SELECT 1", Int::class.java))
            assertRoutineExists(jdbc, "sp_get_user_attributes")
            assertRoutineExists(jdbc, "sp_create_user")

            assertCasJdbcAuthenticationQueries(jdbc)
            assertAttributeStoredProcedure(jdbc)
            assertOverlayProfileAndPasswordService(jdbc, userJdbcService)
            assertLastLoginServiceAndUserCreation(jdbc, userJdbcService, properties)
        }
    }

    private fun withMysql(assertions: (DataSource, Boolean) -> Unit) {
        val localJdbcUrl = System.getenv("CAS_MYSQL_TEST_URL") ?: System.getenv("CAS_MYSQL84_TEST_URL")
        if (!localJdbcUrl.isNullOrBlank()) {
            val dataSource = dataSource(
                jdbcUrl = jdbcUrl(localJdbcUrl),
                username = System.getenv("CAS_MYSQL_TEST_USER") ?: System.getenv("CAS_MYSQL84_TEST_USER") ?: "root",
                password = System.getenv("CAS_MYSQL_TEST_PASSWORD") ?: System.getenv("CAS_MYSQL84_TEST_PASSWORD") ?: "password"
            )
            cleanAndMigrate(dataSource)
            assertions(dataSource, true)
            return
        }

        assumeTrue("Docker is required unless CAS_MYSQL_TEST_URL is set", DockerClientFactory.instance().isDockerAvailable)
        val image = DockerImageName.parse(imageName).asCompatibleSubstituteFor("mysql")
        val mysql = CasMysqlContainer(image)
            .withDatabaseName("cas")
            .withUsername("cas")
            .withPassword("password")

        mysql.start()
        try {
            val dataSource = dataSource(jdbcUrl(mysql.jdbcUrl), mysql.username, mysql.password)
            cleanAndMigrate(dataSource)
            assertions(dataSource, false)
        } finally {
            mysql.stop()
        }
    }

    private fun cleanAndMigrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .run {
                clean()
                migrate()
            }
    }

    private fun dataSource(jdbcUrl: String, username: String, password: String): DataSource = MysqlDataSource().apply {
        setUrl(jdbcUrl)
        user = username
        this.password = password
    }

    private fun jdbcUrl(baseUrl: String): String {
        val separator = if (baseUrl.contains('?')) '&' else '?'
        return "${baseUrl}${separator}serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&nullNamePatternMatchesAll=true"
    }

    private fun assertVersion(jdbc: JdbcTemplate, local: Boolean) {
        val version = jdbc.queryForObject("SELECT VERSION()", String::class.java) ?: ""
        if (!local) {
            assertTrue("Expected MySQL $mysqlVersion but got $version", version.startsWith(mysqlVersion.substringBeforeLast('.')))
        }
    }

    private fun assertRoutineExists(jdbc: JdbcTemplate, routineName: String) {
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE() AND routine_name = ?",
                Int::class.java,
                routineName
            )
        )
    }

    private fun assertCasJdbcAuthenticationQueries(jdbc: JdbcTemplate) {
        val username = "auth-${System.nanoTime()}@example.org"
        val userid = createUser(jdbc, username)
        jdbc.update("INSERT INTO passwords (userid, password, status, type) VALUES (?, ?, 'CURRENT', 'bcrypt')", userid, "bcrypt-hash")
        jdbc.update("INSERT INTO passwords (userid, password, status, type) VALUES (?, ?, 'CURRENT', 'legacy')", userid, "legacy-hash")

        val bcrypt = jdbc.queryForMap(BCRYPT_AUTH_SQL, username)
        assertEquals("bcrypt-hash", bcrypt["password"])
        assertFalse(asBoolean(bcrypt["disabled"]))
        assertFalse(asBoolean(bcrypt["legacy_password"]))

        val legacy = jdbc.queryForMap(LEGACY_AUTH_SQL, username)
        assertEquals("legacy-hash", legacy["password"])
        assertFalse(asBoolean(legacy["disabled"]))
        assertTrue(asBoolean(legacy["legacy_password"]))
    }

    private fun assertAttributeStoredProcedure(jdbc: JdbcTemplate) {
        val username = "attrs-${System.nanoTime()}@example.org"
        val userid = createUser(jdbc, username)
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, 'ROLE_USER')", userid)
        jdbc.update("INSERT INTO profiles (userid, property, value) VALUES (?, 'organisation', 'ALA')", userid)

        val attributes = jdbc.query("call sp_get_user_attributes(?)", { ps -> ps.setString(1, username) }) { rs, _ ->
            rs.getString("key") to rs.getString("value")
        }.toMap()

        assertEquals(username, attributes["email"])
        assertEquals(userid.toString(), attributes["userid"])
        assertEquals("ROLE_USER", attributes["role"])
        assertEquals("ALA", attributes["organisation"])
    }

    private fun assertOverlayProfileAndPasswordService(jdbc: JdbcTemplate, userJdbcService: AlaUserJdbcService) {
        val userid = createUser(jdbc, "profile-${System.nanoTime()}@example.org")

        assertTrue(userJdbcService.shouldOfferSurvey(userid))
        assertEquals(1, userJdbcService.upsertProfile(userid, "affiliation", "community"))
        assertFalse(userJdbcService.shouldOfferSurvey(userid))
        assertEquals(1, userJdbcService.upsertProfile(userid, "affiliation", "education"))

        userJdbcService.updateLegacyPassword(userid, "new-bcrypt-hash")

        assertEquals("new-bcrypt-hash", jdbc.queryForObject("SELECT password FROM passwords WHERE userid = ? AND type = 'bcrypt' AND status = 'CURRENT'", String::class.java, userid))
        assertEquals("education", jdbc.queryForObject("SELECT value FROM profiles WHERE userid = ? AND property = 'affiliation'", String::class.java, userid))
    }

    private fun assertLastLoginServiceAndUserCreation(jdbc: JdbcTemplate, userJdbcService: AlaUserJdbcService, properties: AlaCasProperties) {
        val creator = UserCreatorALA(dataSource = jdbc.dataSource!!, createUserProcedure = properties.userCreator.jdbc.createUserProcedure, userCreatePassword = "password")
        val userid = creator.createUser("created-${System.nanoTime()}@example.org", "Created", "User")
        assertNotNull(userid)

        assertEquals(1, userJdbcService.updateLastLogin(userid!!))
        assertNotNull(jdbc.queryForObject("SELECT last_login FROM users WHERE userid = ?", java.sql.Timestamp::class.java, userid))
        assertEquals("ROLE_USER", jdbc.queryForObject("SELECT role_id FROM user_role WHERE user_id = ?", String::class.java, userid))
    }

    private fun createUser(jdbc: JdbcTemplate, username: String): Long {
        jdbc.update(
            "INSERT INTO users (username, firstname, lastname, email, activated, locked) VALUES (?, 'Test', 'User', ?, '1', '0')",
            username,
            username
        )
        return jdbc.queryForObject("SELECT userid FROM users WHERE username = ?", Long::class.java, username)
    }

    private fun asBoolean(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value == "1" || value.equals("true", ignoreCase = true)
        else -> false
    }

    private class CasMysqlContainer(image: DockerImageName) : MySQLContainer<CasMysqlContainer>(image)
}

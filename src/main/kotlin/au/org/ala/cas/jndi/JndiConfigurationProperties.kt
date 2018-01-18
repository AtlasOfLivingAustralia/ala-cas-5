package au.org.ala.cas.jndi

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties mapping for generating jndi datasources within an application.
 *
 * At the moment only hikari connection pool data sources are supported.
 */
@ConfigurationProperties(value = "jndi")
class JndiConfigurationProperties(var hikari: List<HikariDatasource> = mutableListOf())

open class HikariDatasource(
        var name: String = "jdbc/ds",
        var driverClass: String? = null, // Nullable because Hikari can determine the driver from the URL
        var url: String = "jdbc:hsqldb:mem:cas-hsql-database",
        var user: String = "sa",
        var password: String = "",
        var autoCommit: Boolean? = null,
        var connectionTimeout: Long? = null,
        var idleTimeout: Long? = null,
        var maxLifetime: Long? = null,
        var connectionTestQuery: String? = null,
        var minimumIdle: Int? = null,
        var maximumPoolSize: Int? = null,
        var poolName: String? = null,
        var initializationFailTimeout: Long? = null,
        var isolateInternalQueries: Boolean? = null,
        var allowPoolSuspension: Boolean? = null,
        var readOnly: Boolean? = null,
        var registerMbeans: Boolean? = null,
        var catalog: String? = null,
        var connectionInitSql: String? = null,
        var transactionIsolation: String? = null,
        var validationTimeout: Long? = null,
        var leakDetectionThreshold: Long? = null,
        var dataSourceProperties: MutableMap<String, String> = mutableMapOf(),
        var schema: String? = null
)
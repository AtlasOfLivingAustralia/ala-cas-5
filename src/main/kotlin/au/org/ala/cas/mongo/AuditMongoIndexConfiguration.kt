package au.org.ala.cas.mongo

import au.org.ala.cas.AlaCasProperties
import au.org.ala.utils.logger
import com.mongodb.ConnectionString
import com.mongodb.MongoSocketReadTimeoutException
import org.apereo.cas.authentication.CasSSLContext
import org.apereo.cas.configuration.CasConfigurationProperties
import org.apereo.cas.configuration.model.core.audit.AuditMongoDbProperties
import org.apereo.cas.mongo.MongoDbConnectionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

/**
 * Indexes the CAS audit repository when it lives in MongoDb. See [AuditMongoIndexService] for why.
 *
 * Unlike [MongoIndexConfiguration], this cannot autowire the template it needs:
 * `CasSupportMongoDbAuditConfiguration` and `CasMongoDbThrottlingConfiguration` each build their
 * own `MongoTemplate` inline from `cas.audit.mongo.*` and never publish it as a bean. So this
 * builds its own client the same way CAS does and closes it again afterwards, rather than leaving
 * a third connection pool to the audit database open for the life of the process. (Handing an
 * existing client to `SimpleMongoClientDatabaseFactory`, as CAS does, makes its `destroy()` a
 * no-op, so the client has to be closed directly.)
 *
 * It runs on [ApplicationReadyEvent] in a daemon thread rather than in `@PostConstruct` on
 * purpose: building this index over a collection of millions of audit records takes tens of
 * seconds, and CAS should be serving logins (slowly) in the meantime rather than not starting.
 * Should the client give up first - `cas.audit.mongo.timeout`, or `socketTimeoutMS` in the client
 * URI, is the socket read timeout - the server still finishes the build, and the next start finds
 * the index in place and does nothing.
 */
@Configuration("auditMongoIndexConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties::class, AlaCasProperties::class)
@ConditionalOnProperty(value = ["ala.audit.mongo.indexes-enabled"], havingValue = "true", matchIfMissing = true)
class AuditMongoIndexConfiguration {

    companion object {
        private val log = logger()
        private const val THREAD_NAME = "ala-cas-audit-mongo-index"
    }

    @Autowired
    lateinit var casProperties: CasConfigurationProperties

    @Autowired
    lateinit var alaCasProperties: AlaCasProperties

    @Autowired
    @Qualifier(CasSSLContext.BEAN_NAME)
    lateinit var casSslContext: CasSSLContext

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        val mongo = casProperties.audit.mongo
        if (mongo.clientUri.isNullOrBlank() && mongo.databaseName.isNullOrBlank()) {
            log.info(
                "The CAS audit repository is not stored in MongoDb (neither cas.audit.mongo.client-uri " +
                    "nor cas.audit.mongo.database-name is set); not indexing anything"
            )
            return
        }
        Thread({ indexAuditCollection(mongo) }, THREAD_NAME).apply { isDaemon = true }.start()
    }

    internal fun indexAuditCollection(mongo: AuditMongoDbProperties) {
        val client = MongoDbConnectionFactory(casSslContext.sslContext).buildMongoDbClient(mongo)
        try {
            val mongoTemplate = MongoTemplate(SimpleMongoClientDatabaseFactory(client, databaseName(mongo)))
            AuditMongoIndexService(
                mongoTemplate,
                mongo.collection,
                alaCasProperties.audit.mongo.retentionDays
            ).ensureIndexes()
        } catch (e: MongoSocketReadTimeoutException) {
            // Expected on the first start over an already-large collection: the socket read timeout
            // is shorter than the index build. MongoDb carries the build on regardless, and the
            // next start reads the finished index back, so this is not worth a stack trace.
            log.warn(
                "Timed out waiting for MongoDb to index the CAS audit collection [{}]. The server " +
                    "finishes the build on its own; the next start verifies it. ({})",
                mongo.collection, e.message
            )
        } catch (e: Exception) {
            log.warn(
                "Could not index the CAS audit collection [{}]. Authentication throttling will scan the " +
                    "whole collection until this succeeds; it is retried on the next start.",
                mongo.collection, e
            )
        } finally {
            client.close()
        }
    }

    /** The database CAS itself resolves the audit collection against. */
    private fun databaseName(mongo: AuditMongoDbProperties): String =
        if (!mongo.databaseName.isNullOrBlank()) {
            mongo.databaseName
        } else {
            requireNotNull(ConnectionString(mongo.clientUri).database) {
                "cas.audit.mongo.client-uri does not name a database"
            }
        }
}

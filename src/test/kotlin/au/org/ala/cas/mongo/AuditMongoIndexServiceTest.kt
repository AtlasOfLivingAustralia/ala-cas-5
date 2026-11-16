package au.org.ala.cas.mongo

import au.org.ala.cas.mongo.AuditMongoIndexService.Companion.FIELD_CLIENT_IP_ADDRESS
import au.org.ala.cas.mongo.AuditMongoIndexService.Companion.FIELD_WHEN_ACTION_WAS_PERFORMED
import au.org.ala.cas.mongo.AuditMongoIndexService.Companion.THROTTLE_INDEX_NAME
import au.org.ala.cas.mongo.AuditMongoIndexService.Companion.TTL_INDEX_NAME
import au.org.ala.cas.AlaCasProperties
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.apereo.cas.authentication.CasSSLContext
import org.apereo.cas.configuration.CasConfigurationProperties
import org.apereo.inspektr.audit.AuditActionContext
import org.bson.Document
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Integration tests for [AuditMongoIndexService] against a real MongoDb.
 *
 * Point CAS_MONGO_TEST_URI at an existing MongoDb to skip Docker.
 */
class AuditMongoIndexServiceTest {

    companion object {
        private const val IMAGE = "mongo:7"
        private const val DEFAULT_DATABASE = "cas-audit-repository"

        private var container: MongoDBContainer? = null
        private var client: MongoClient? = null

        /** The audit database, and a client URI naming it, as cas.audit.mongo.client-uri would. */
        private lateinit var database: String
        private lateinit var auditClientUri: String
        private lateinit var mongoTemplate: MongoTemplate

        @BeforeClass
        @JvmStatic
        fun startMongo() {
            val uri = System.getenv("CAS_MONGO_TEST_URI")
            val base = if (!uri.isNullOrBlank()) {
                uri
            } else {
                assumeTrue(
                    "Docker is required unless CAS_MONGO_TEST_URI is set",
                    DockerClientFactory.instance().isDockerAvailable
                )
                MongoDBContainer(DockerImageName.parse(IMAGE))
                    .also { container = it; it.start() }
                    .connectionString
            }
            val named = ConnectionString(base).database
            database = named ?: DEFAULT_DATABASE
            auditClientUri = if (named != null) base else base.trimEnd('/') + "/" + DEFAULT_DATABASE
            val mongoClient = MongoClients.create(base)
            client = mongoClient
            mongoTemplate = MongoTemplate(SimpleMongoClientDatabaseFactory(mongoClient, database))
        }

        @AfterClass
        @JvmStatic
        fun stopMongo() {
            client?.close()
            container?.stop()
        }
    }

    private fun service(collection: String, retentionDays: Int = 0) =
        AuditMongoIndexService(mongoTemplate, collection, retentionDays)

    private fun collectionName(name: String) = "audit_${name}_${System.nanoTime()}"

    private fun indexes(collection: String): List<Document> =
        mongoTemplate.getCollection(collection).listIndexes().into(ArrayList())

    private fun index(collection: String, name: String): Document? =
        indexes(collection).firstOrNull { it.getString("name") == name }

    /**
     * On a fresh install CAS has not written an audit record yet, so the collection does not
     * exist. listIndexes() raises NamespaceNotFound in that state, which is why the service
     * creates the collection first.
     */
    @Test
    fun `creates the collection and the throttle index on a fresh install`() {
        val collection = collectionName("fresh")
        assertFalse(mongoTemplate.collectionExists(collection))

        val names = service(collection).ensureIndexes()

        assertTrue(mongoTemplate.collectionExists(collection))
        assertEquals(listOf("_id_", THROTTLE_INDEX_NAME), names)
        assertEquals(
            Document(FIELD_CLIENT_IP_ADDRESS, 1).append(FIELD_WHEN_ACTION_WAS_PERFORMED, -1),
            index(collection, THROTTLE_INDEX_NAME)!!["key"]
        )
        // Retention is off by default: the audit trail is personal data.
        assertNull(index(collection, TTL_INDEX_NAME))
    }

    @Test
    fun `is idempotent across restarts`() {
        val collection = collectionName("idempotent")
        val first = service(collection).ensureIndexes()
        val second = service(collection).ensureIndexes()
        val third = service(collection).ensureIndexes()

        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(2, indexes(collection).size)
    }

    @Test
    fun `creates, changes and removes the retention index`() {
        val collection = collectionName("ttl")

        service(collection, retentionDays = 30).ensureIndexes()
        assertEquals(30L * 86400L, expireAfterSeconds(collection))

        // createIndex cannot change expireAfterSeconds on an existing index, so this exercises
        // the collMod path - and must not drop and rebuild the index.
        service(collection, retentionDays = 7).ensureIndexes()
        assertEquals(7L * 86400L, expireAfterSeconds(collection))

        service(collection, retentionDays = 7).ensureIndexes()
        assertEquals(7L * 86400L, expireAfterSeconds(collection))

        // Back to the default: retention off, so the TTL index goes away and nothing is purged.
        val names = service(collection, retentionDays = 0).ensureIndexes()
        assertNull(index(collection, TTL_INDEX_NAME))
        assertEquals(listOf("_id_", THROTTLE_INDEX_NAME), names)
    }

    @Test
    fun `replaces a throttle index whose keys have changed`() {
        val collection = collectionName("renamed")
        mongoTemplate.createCollection(collection)
        // An index left over from an earlier shape of the query, under the name we want.
        mongoTemplate.getCollection(collection).createIndex(
            Document(FIELD_CLIENT_IP_ADDRESS, 1),
            com.mongodb.client.model.IndexOptions().name(THROTTLE_INDEX_NAME)
        )

        service(collection).ensureIndexes()

        assertEquals(
            Document(FIELD_CLIENT_IP_ADDRESS, 1).append(FIELD_WHEN_ACTION_WAS_PERFORMED, -1),
            index(collection, THROTTLE_INDEX_NAME)!!["key"]
        )
        assertEquals(2, indexes(collection).size)
    }

    /**
     * The point of the whole exercise: the query that CAS runs on every authentication attempt,
     * from MongoDbThrottledSubmissionHandlerInterceptorAdapter.exceedsThreshold, must not scan
     * the collection.
     */
    @Test
    fun `the throttle query uses the index instead of scanning the collection`() {
        val collection = collectionName("plan")
        seedAuditRecords(collection, 2000)

        val planBefore = explainThrottleQuery(collection)
        assertTrue("expected a COLLSCAN before indexing but got: $planBefore", planBefore.contains("COLLSCAN"))

        service(collection).ensureIndexes()

        val planAfter = explainThrottleQuery(collection)
        assertTrue("expected an IXSCAN but got: $planAfter", planAfter.contains("IXSCAN"))
        assertTrue("expected $THROTTLE_INDEX_NAME to be used but got: $planAfter", planAfter.contains(THROTTLE_INDEX_NAME))
        assertFalse("expected no COLLSCAN but got: $planAfter", planAfter.contains("COLLSCAN"))
    }

    /**
     * A TTL index only expires documents whose field is a BSON date. If CAS stored
     * whenActionWasPerformed as a string, retention would silently never purge anything.
     */
    @Test
    fun `audit records store whenActionWasPerformed as a BSON date`() {
        val collection = collectionName("fieldtypes")
        mongoTemplate.save(auditActionContext("10.0.0.1", Date()), collection)

        val stored = mongoTemplate.getCollection(collection)
            .find(Document(FIELD_WHEN_ACTION_WAS_PERFORMED, Document("\$type", "date")))
            .first()

        assertNotNull("whenActionWasPerformed is not stored as a BSON date", stored)
        assertEquals("10.0.0.1", stored!!.getString(FIELD_CLIENT_IP_ADDRESS))
    }

    /**
     * The configuration's own path: it builds the client the way CAS does, from
     * cas.audit.mongo.client-uri, and closes it again.
     */
    @Test
    fun `the configuration indexes the collection CAS is configured with`() {
        val collection = collectionName("config")
        val casProperties = CasConfigurationProperties()
        casProperties.audit.mongo.setCollection(collection)
        casProperties.audit.mongo.setClientUri(auditClientUri)

        val configuration = AuditMongoIndexConfiguration().apply {
            this.casProperties = casProperties
            this.alaCasProperties = AlaCasProperties().apply { audit.mongo.retentionDays = 14 }
            this.casSslContext = CasSSLContext.disabled()
        }
        configuration.indexAuditCollection(casProperties.audit.mongo)

        assertEquals(
            Document(FIELD_CLIENT_IP_ADDRESS, 1).append(FIELD_WHEN_ACTION_WAS_PERFORMED, -1),
            index(collection, THROTTLE_INDEX_NAME)!!["key"]
        )
        assertEquals(14L * 86400L, expireAfterSeconds(collection))
    }

    /** Nothing to do, and nothing to blow up on, when the audit trail is not in MongoDb. */
    @Test
    fun `does nothing when the audit repository is not in mongo`() {
        val collection = collectionName("absent")
        val casProperties = CasConfigurationProperties()
        casProperties.audit.mongo.setCollection(collection)

        AuditMongoIndexConfiguration().apply {
            this.casProperties = casProperties
            this.alaCasProperties = AlaCasProperties()
            this.casSslContext = CasSSLContext.disabled()
        }.onApplicationReady()

        assertFalse(mongoTemplate.collectionExists(collection))
    }

    /**
     * The legacy `mongo` shell writes index directions as doubles, so an index created by hand
     * before this existed comes back as `{ clientIpAddress: 1.0 }`. That is the same index, and
     * must not be dropped and rebuilt on every start.
     */
    @Test
    fun `keeps an equivalent index whose directions are doubles`() {
        val collection = collectionName("doubles")
        mongoTemplate.createCollection(collection)
        val doubleKeys = Document(FIELD_CLIENT_IP_ADDRESS, 1.0).append(FIELD_WHEN_ACTION_WAS_PERFORMED, -1.0)
        mongoTemplate.getCollection(collection).createIndex(
            doubleKeys,
            com.mongodb.client.model.IndexOptions().name(THROTTLE_INDEX_NAME)
        )

        service(collection).ensureIndexes()

        assertEquals(doubleKeys, index(collection, THROTTLE_INDEX_NAME)!!["key"])
        assertEquals(2, indexes(collection).size)
    }

    private fun expireAfterSeconds(collection: String): Long? =
        (index(collection, TTL_INDEX_NAME)?.get("expireAfterSeconds") as? Number)?.toLong()

    private fun seedAuditRecords(collection: String, count: Int) {
        val now = System.currentTimeMillis()
        val documents = (0 until count).map { i ->
            auditActionContext("10.0.0.${i % 250}", Date(now - TimeUnit.SECONDS.toMillis(i.toLong())))
        }
        mongoTemplate.insert(documents, collection)
    }

    private fun auditActionContext(clientIp: String, whenPerformed: Date) = AuditActionContext(
        null,
        "supplied credentials: [username: someone@example.org]",
        "AUTHENTICATION_FAILED",
        "CAS",
        whenPerformed,
        clientIp,
        "10.0.0.254",
        "Mozilla/5.0"
    )

    /**
     * Builds the throttle query exactly as CAS does - same five predicates, same descending sort
     * on whenActionWasPerformed, same limit - and returns the server's winning plan as JSON.
     *
     * Only the winning plan: rejectedPlans legitimately holds a COLLSCAN once the index exists.
     */
    private fun explainThrottleQuery(collection: String): String {
        val cutOffDate = Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10))
        val query = Query().addCriteria(
            Criteria.where(FIELD_CLIENT_IP_ADDRESS).`is`("10.0.0.7")
                .and("principal").`is`(null)
                .and("actionPerformed").`is`("AUTHENTICATION_FAILED")
                .and("applicationCode").`is`("CAS")
                .and(FIELD_WHEN_ACTION_WAS_PERFORMED).gte(cutOffDate)
        )
        query.with(Sort.by(Sort.Direction.DESC, FIELD_WHEN_ACTION_WAS_PERFORMED))
        query.limit(2)

        val explain = mongoTemplate.executeCommand(
            Document(
                "explain",
                Document("find", collection)
                    .append("filter", query.queryObject)
                    .append("sort", query.sortObject)
                    .append("limit", query.limit)
            )
        )
        return ((explain["queryPlanner"] as Document)["winningPlan"] as Document).toJson()
    }
}

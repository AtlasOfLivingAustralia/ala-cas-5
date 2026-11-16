package au.org.ala.cas.mongo

import au.org.ala.utils.logger
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import org.springframework.data.mongodb.core.MongoOperations
import java.util.concurrent.TimeUnit

/**
 * Ensures the indexes that the CAS audit repository needs when it is stored in MongoDb.
 *
 * CAS reads this collection on *every* authentication attempt, from
 * `MongoDbThrottledSubmissionHandlerInterceptorAdapter.exceedsThreshold`, but nothing ever
 * indexes it: Apereo's `MongoDbAuditTrailManager` only implements `saveAuditRecord`,
 * `getAuditRecords` and `removeAll`. Once the collection grows, every login degrades into a
 * COLLSCAN with an in-memory sort, which blows past `cas.audit.mongo.timeout` and surfaces as
 * a 500 on `/cas/login`.
 *
 * Index shape: `clientIpAddress` is the only selective field of the throttle query -
 * `applicationCode` is always `"CAS"` and `principal` is null for the vast majority of the
 * throttle lookups, so including them would only inflate the index and slow down every audit
 * write. `whenActionWasPerformed` serves both the `$gte` range and the descending sort; the
 * remaining predicates stay as a residual filter over a handful of documents.
 *
 * Everything here is idempotent: it is safe to run on every start.
 */
class AuditMongoIndexService(
    private val mongoTemplate: MongoOperations,
    private val collectionName: String,
    private val retentionDays: Int
) {

    companion object {
        private val log = logger()

        const val THROTTLE_INDEX_NAME = "idx_cas_throttle_lookup"
        const val TTL_INDEX_NAME = "idx_cas_audit_ttl"

        const val FIELD_CLIENT_IP_ADDRESS = "clientIpAddress"
        const val FIELD_WHEN_ACTION_WAS_PERFORMED = "whenActionWasPerformed"

        private const val EXPIRE_AFTER_SECONDS = "expireAfterSeconds"
        private const val SECONDS_PER_DAY = 86_400L

        /** The throttle lookup index: equality on the client IP, then the range + sort field. */
        val THROTTLE_INDEX_KEYS: Document =
            Document(FIELD_CLIENT_IP_ADDRESS, 1).append(FIELD_WHEN_ACTION_WAS_PERFORMED, -1)

        /** A TTL index must be single field and ascending. */
        val TTL_INDEX_KEYS: Document = Document(FIELD_WHEN_ACTION_WAS_PERFORMED, 1)
    }

    /**
     * Creates the collection if needed, then converges its indexes on the configured state.
     *
     * @return the names of the indexes on the collection afterwards.
     */
    fun ensureIndexes(): List<String> {
        if (!mongoTemplate.collectionExists(collectionName)) {
            // On a fresh install CAS has not written its first audit record yet, so the
            // collection does not exist and listIndexes() would fail with NamespaceNotFound.
            // CAS would create it anyway; this way the index is in place before the first write.
            log.info("Creating CAS audit collection [{}]", collectionName)
            mongoTemplate.createCollection(collectionName)
        }
        ensureThrottleIndex()
        ensureRetentionIndex()
        val names = indexNames()
        log.info("Indexes on CAS audit collection [{}] are now {}", collectionName, names)
        return names
    }

    private fun collection() = mongoTemplate.getCollection(collectionName)

    /**
     * Index directions as integers, whatever the server reports them as. The legacy `mongo` shell
     * writes them as doubles, so an index created by hand from a script can come back as
     * `{ clientIpAddress: 1.0 }` - equivalent to `1`, but not equal to it. Without this, such an
     * index would be dropped and rebuilt on every start.
     */
    private fun normalisedKeys(index: Document): Document {
        val keys = index["key"] as? Document ?: return Document()
        val normalised = Document()
        keys.forEach { (field, value) -> normalised.append(field, if (value is Number) value.toInt() else value) }
        return normalised
    }

    private fun indexes(): List<Document> = collection().listIndexes().into(ArrayList())

    private fun indexNames(): List<String> = indexes().mapNotNull { it.getString("name") }

    private fun ensureThrottleIndex() {
        val existing = indexes()
        if (existing.any { normalisedKeys(it) == THROTTLE_INDEX_KEYS }) {
            log.debug("Index {} already exists on [{}]", THROTTLE_INDEX_KEYS, collectionName)
            return
        }
        if (existing.any { it.getString("name") == THROTTLE_INDEX_NAME }) {
            // Same name, different keys. Mongo refuses to recreate an index under a name that
            // is already taken, so the stale one has to go first.
            log.info("Dropping index [{}] on [{}] because its keys have changed", THROTTLE_INDEX_NAME, collectionName)
            collection().dropIndex(THROTTLE_INDEX_NAME)
        }
        log.info("Creating index [{}] {} on [{}]", THROTTLE_INDEX_NAME, THROTTLE_INDEX_KEYS, collectionName)
        collection().createIndex(THROTTLE_INDEX_KEYS, IndexOptions().name(THROTTLE_INDEX_NAME))
    }

    /**
     * Retention is off by default: the audit trail is personal data and the retention period is
     * each institution's policy to set. Enabling it on an already-large collection purges the
     * backlog in the background - do it off-peak.
     */
    private fun ensureRetentionIndex() {
        val existing = indexes().firstOrNull { it.getString("name") == TTL_INDEX_NAME }
        if (retentionDays <= 0) {
            if (existing != null) {
                log.info("Dropping TTL index [{}] on [{}]: retention is disabled", TTL_INDEX_NAME, collectionName)
                collection().dropIndex(TTL_INDEX_NAME)
            }
            return
        }
        val wanted = retentionDays * SECONDS_PER_DAY
        if (existing == null) {
            log.info("Creating TTL index [{}] on [{}] expiring after {} days", TTL_INDEX_NAME, collectionName, retentionDays)
            collection().createIndex(
                TTL_INDEX_KEYS,
                IndexOptions().name(TTL_INDEX_NAME).expireAfter(wanted, TimeUnit.SECONDS)
            )
            return
        }
        val current = (existing[EXPIRE_AFTER_SECONDS] as? Number)?.toLong()
        if (current == wanted) {
            log.debug("TTL index [{}] on [{}] already expires after {}s", TTL_INDEX_NAME, collectionName, wanted)
            return
        }
        // createIndex cannot change expireAfterSeconds on an index that already exists (it fails
        // with IndexOptionsConflict), and dropping it would rebuild the whole index. collMod
        // changes it in place.
        log.info(
            "Changing TTL index [{}] on [{}] from {}s to {}s",
            TTL_INDEX_NAME, collectionName, current, wanted
        )
        mongoTemplate.executeCommand(
            Document("collMod", collectionName)
                .append("index", Document("name", TTL_INDEX_NAME).append(EXPIRE_AFTER_SECONDS, wanted))
        )
    }
}

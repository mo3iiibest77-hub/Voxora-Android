package com.voxora.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for reading Google Cloud payloads.
 *
 * A parser must never turn "we could not read this" into a number. The malformed cases therefore
 * assert `null`, not `0`, and only a genuine empty Monitoring window is allowed to be `0`.
 */
class CloudParsersTest {

    // ---- projects -------------------------------------------------------------------

    @Test
    fun readsProjectsWithTheirIdentity() {
        val body = """
            {"projects":[
              {"projectId":"alpha-123","projectNumber":"111","name":"Alpha","lifecycleState":"ACTIVE"},
              {"projectId":"beta-456","projectNumber":"222","name":"Beta","lifecycleState":"ACTIVE"}
            ]}
        """.trimIndent()
        val projects = CloudParsers.projects(body)!!
        assertEquals(2, projects.size)
        assertEquals("alpha-123", projects[0].projectId)
        assertEquals("Alpha", projects[0].displayName)
        assertEquals("111", projects[0].projectNumber)
        assertEquals("ACTIVE", projects[0].lifecycleState)
    }

    @Test
    fun aProjectWithNoIdIsDroppedRatherThanKeptUnaddressable() {
        val body = """{"projects":[{"name":"No id"},{"projectId":"alpha-123","name":"Alpha"}]}"""
        val projects = CloudParsers.projects(body)!!
        assertEquals(1, projects.size)
        assertEquals("alpha-123", projects[0].projectId)
    }

    @Test
    fun aProjectWithoutANameFallsBackToItsId() {
        val projects = CloudParsers.projects("""{"projects":[{"projectId":"alpha-123"}]}""")!!
        assertEquals("alpha-123", projects[0].label)
    }

    @Test
    fun anEmptyProjectListIsAnHonestEmptyAnswer() {
        assertTrue(CloudParsers.projects("""{"projects":[]}""")!!.isEmpty())
    }

    @Test
    fun anUnreadableProjectBodyIsNullNotAnEmptyList() {
        assertNull(CloudParsers.projects("not json"))
        assertNull(CloudParsers.projects(""))
        assertNull(CloudParsers.projects(null))
        // A valid JSON body of the wrong shape is not an empty project list either.
        assertNull(CloudParsers.projects("""{"error":{"code":403}}"""))
    }

    // ---- keys -----------------------------------------------------------------------

    @Test
    fun readsKeyMetadataWithoutAnySecret() {
        val body = """
            {"keys":[
              {"name":"projects/alpha-123/locations/global/keys/key-1",
               "displayName":"Reader key",
               "restrictions":{"apiTargets":[{"service":"generativelanguage.googleapis.com"}]}}
            ]}
        """.trimIndent()
        val keys = CloudParsers.keys(body)!!
        assertEquals(1, keys.size)
        assertEquals("key-1", keys[0].keyId)
        assertEquals("Reader key", keys[0].displayName)
        assertTrue(keys[0].restricted)
        assertEquals("projects/alpha-123/locations/global/keys/key-1", keys[0].resourceName)
    }

    @Test
    fun anUnrestrictedKeyIsReportedAsSuch() {
        val keys = CloudParsers.keys(
            """{"keys":[{"name":"projects/p/locations/global/keys/k","displayName":"K"}]}""",
        )!!
        assertTrue(!keys[0].restricted)
    }

    @Test
    fun aKeyWithoutAResourceNameIsDropped() {
        val keys = CloudParsers.keys("""{"keys":[{"displayName":"Orphan"},{"name":"projects/p/locations/global/keys/k"}]}""")!!
        assertEquals(1, keys.size)
        assertEquals("k", keys[0].keyId)
    }

    @Test
    fun anUnreadableKeyBodyIsNull() {
        assertNull(CloudParsers.keys("nope"))
        assertNull(CloudParsers.keys("""{"projects":[]}"""))
    }

    // ---- monitoring request count ---------------------------------------------------

    @Test
    fun sumsEveryPointOfTheRequestCount() {
        val body = """
            {"timeSeries":[
              {"points":[{"value":{"int64Value":"120"}},{"value":{"int64Value":"80"}}]},
              {"points":[{"value":{"int64Value":"5"}}]}
            ]}
        """.trimIndent()
        assertEquals(205L, CloudParsers.requestCount(body))
    }

    @Test
    fun acceptsDoubleValuesAsWellAsIntegerOnes() {
        assertEquals(7L, CloudParsers.requestCount("""{"timeSeries":[{"points":[{"value":{"doubleValue":7.0}}]}]}"""))
    }

    /** A valid empty window genuinely means zero requests, and that is a real zero. */
    @Test
    fun aValidEmptyWindowIsZero() {
        assertEquals(0L, CloudParsers.requestCount("""{"timeSeries":[]}"""))
        assertEquals(0L, CloudParsers.requestCount("""{}"""))
    }

    @Test
    fun anUnreadableMonitoringBodyIsNullNotZero() {
        assertNull(CloudParsers.requestCount("not json"))
        assertNull(CloudParsers.requestCount(null))
    }

    @Test
    fun aPointWithNoUsableValueContributesNothing() {
        assertEquals(3L, CloudParsers.requestCount("""{"timeSeries":[{"points":[{"value":{}},{"value":{"int64Value":"3"}}]}]}"""))
    }

    // ---- quota ----------------------------------------------------------------------

    @Test
    fun readsTheGenerativeContentQuotaLimit() {
        val body = """
            {"quotaInfos":[
              {"quotaId":"ReadRequestsPerMinutePerProject","metric":"generativelanguage.googleapis.com/read",
               "dimensionsInfos":[{"details":{"value":"60"}}]},
              {"quotaId":"GenerateContentRequestsPerMinutePerProject",
               "metric":"generativelanguage.googleapis.com/generate_content",
               "dimensionsInfos":[{"details":{"value":"2000"}}]}
            ]}
        """.trimIndent()
        assertEquals(2000L, CloudParsers.quotaLimit(body))
    }

    @Test
    fun aQuotaResponseWithNoGenerativeContentQuotaIsNull() {
        val body = """{"quotaInfos":[{"quotaId":"ReadRequestsPerMinutePerProject","dimensionsInfos":[{"details":{"value":"60"}}]}]}"""
        assertNull(CloudParsers.quotaLimit(body))
    }

    @Test
    fun anUnreadableQuotaBodyIsNull() {
        assertNull(CloudParsers.quotaLimit("nope"))
        assertNull(CloudParsers.quotaLimit(null))
    }
}

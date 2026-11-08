package com.asoc.ping.export

import com.fasterxml.jackson.databind.ObjectMapper
import com.asoc.ping.data.export.IssuesManifestBuilder
import com.asoc.ping.domain.model.Incident
import com.asoc.ping.domain.model.IncidentStatus
import com.asoc.ping.domain.model.Severity
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.model.SignalType
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportConformanceTest {
    private val mapper = ObjectMapper()
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(javaClass.classLoader.getResourceAsStream("hub-contract/issues-manifest.v1.schema.json"))

    private fun inc(id: String, status: IncidentStatus = IncidentStatus.OPEN, updated: Long = 2000) =
        Incident(id, "Title $id", "sum", Severity.HIGH, status, false, createdAt = 1000, updatedAt = updated, projectId = "p")
    private fun sig(k: String, inc: String, upd: Long = 1500) = Signal(sourceKey = k, type = SignalType.EMAIL, title = "t", body = "b", authorName = "A", rating = null, createdAt = 1, sourceUpdatedAt = upd, ingestedAt = 1, incidentId = inc, projectId = "p")

    private fun build(incs: List<Incident>, sigs: Map<String, List<Signal>> = emptyMap()) =
        IssuesManifestBuilder.build(incs, sigs, "1.0.0", "csapp:p", projectCreatedAt = 500)

    @Test fun `output validates against the ratified JSON schema (real validator)`() {
        val json = IssuesManifestBuilder.toJson(build(listOf(inc("inc_a"), inc("inc_b")), mapOf("inc_a" to listOf(sig("mail:1:x", "inc_a")))))
        val errors = schema.validate(mapper.readTree(json))
        assertTrue(errors.toString(), errors.isEmpty())
        val empty = schema.validate(mapper.readTree(IssuesManifestBuilder.toJson(build(emptyList()))))
        assertTrue(empty.toString(), empty.isEmpty())
    }

    @Test fun `unchanged data exports byte-identical output, a changed updatedAt changes exportId and bytes`() {
        val a = IssuesManifestBuilder.toJson(build(listOf(inc("inc_a"), inc("inc_b"))))
        val b = IssuesManifestBuilder.toJson(build(listOf(inc("inc_b"), inc("inc_a")))) // input order must not matter
        assertEquals(a, b)
        val changed = build(listOf(inc("inc_a", updated = 3000), inc("inc_b")))
        assertNotEquals(IssuesManifestBuilder.toJson(build(listOf(inc("inc_a"), inc("inc_b")))), IssuesManifestBuilder.toJson(changed))
        assertNotEquals(build(listOf(inc("inc_a"))).exportId, changed.exportId)
        assertEquals("1970-01-01T00:00:03Z", changed.exportedAt)
    }

    @Test fun `dismissed and merged incidents are excluded, empty export uses project createdAt`() {
        val m = build(listOf(inc("a"), inc("b", IncidentStatus.DISMISSED), inc("c", IncidentStatus.MERGED), inc("d", IncidentStatus.RESOLVED)))
        assertEquals(listOf("a", "d"), m.issues.map { it.id })
        assertEquals("1970-01-01T00:00:00.500Z", build(emptyList()).exportedAt)
    }

    @Test fun `sourceRevision is the newest signal sourceUpdatedAt so later edits show as changed`() {
        val m = build(listOf(inc("a")), mapOf("a" to listOf(sig("k1", "a", 1500), sig("k2", "a", 1800))))
        assertEquals("1970-01-01T00:00:01.800Z", m.issues.single().sourceRevision)
        IssuesManifestBuilder.validate(m)
    }

    @Test fun `exportId is a sha256 hex of the canonical manifest without it`() {
        val m = build(listOf(inc("a")))
        assertTrue(Regex("[0-9a-f]{64}").matches(m.exportId))
        assertEquals(m.exportId, IssuesManifestBuilder.computeExportId(m))
    }
}

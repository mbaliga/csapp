package com.mbaliga.csapp.export

import com.mbaliga.csapp.data.export.IssuesManifestBuilder
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import java.time.format.DateTimeParseException
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Validates an emitted `issues-manifest.json` against the ratified Fonebrew hub contract
 * (`docs/ratified/CSAPP_ISSUES_MANIFEST_V1.md`, `schemas/integrations/
 * issues-manifest.v1.schema.json`, both in `mbaliga/Android-IDE-core`).
 *
 * NOTE ON METHOD: this project has no JSON-Schema validator dependency (no `networknt
 * json-schema-validator` / `everit-org json-schema` / equivalent on the test classpath - checked
 * `app/build.gradle.kts` before writing this). Per the house rule ("real JSON-schema validation
 * if a validator dep exists, else exact structural assertions"), this test instead does
 * structural assertions - but drives them FROM the literal copied schema file below rather than
 * hardcoding a second copy of its required-field list, pattern, and enum, so the test can't
 * silently drift from the contract it's supposed to check:
 *   `app/src/test/resources/hub-contract/issues-manifest.v1.schema.json` - byte-for-byte copy of
 *   the ratified schema.
 *   `app/src/test/resources/hub-contract/issues-manifest-baseline-valid.json` - byte-for-byte
 *   copy of core's own "valid" fixture, used below for a field-for-field shape comparison.
 * If a real validator dependency is ever added to this project, this test should be switched to
 * use it directly instead.
 */
class HubContractConformanceTest {

    private val schema: JsonObject = loadResource("hub-contract/issues-manifest.v1.schema.json").jsonObject
    private val baselineFixture: JsonObject = loadResource("hub-contract/issues-manifest-baseline-valid.json").jsonObject

    private fun loadResource(path: String) =
        Json.parseToJsonElement(
            requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing test resource: $path" }
                .bufferedReader()
                .readText(),
        )

    private fun emitSampleManifest(): JsonObject {
        val incidentWithSignal = Incident(
            id = "inc_1",
            title = "Crash on markdown-heavy reply",
            summary = "1 signal(s)",
            severity = Severity.HIGH,
            status = IncidentStatus.ACKNOWLEDGED,
            isManual = false,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
        )
        val signal = Signal(
            sourceKey = "github:owner/repo#5",
            type = SignalType.GITHUB_ISSUE,
            title = "Crash on markdown-heavy reply",
            body = "Repro attached.",
            authorName = "octocat",
            rating = null,
            createdAt = 1_700_000_000_000,
            sourceUpdatedAt = 1_700_000_000_000,
            ingestedAt = 1_700_000_000_000,
            replyState = ReplyState.NONE,
        )
        val manualIncidentNoSignals = Incident(
            id = "inc_2",
            title = "Manually logged issue",
            summary = "Filed by support directly",
            severity = Severity.LOW,
            status = IncidentStatus.OPEN,
            isManual = true,
            createdAt = 1_700_000_200_000,
            updatedAt = 1_700_000_200_000,
        )

        val manifest = IssuesManifestBuilder.build(
            incidents = listOf(incidentWithSignal, manualIncidentNoSignals),
            signalsByIncidentId = mapOf(incidentWithSignal.id to listOf(signal)),
            producerVersion = "0.1.0",
            projectExternalId = "github:mbaliga/csapp",
            nowMillis = 1_700_000_300_000,
            exportId = "conformance-test-export",
        )
        return Json.parseToJsonElement(IssuesManifestBuilder.toJson(manifest)).jsonObject
    }

    // -- top level -----------------------------------------------------------------------------

    @Test
    fun `emitted manifest has every schema-required top-level field`() {
        val manifest = emitSampleManifest()
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }

        required.forEach { field ->
            assertTrue("missing required top-level field '$field'", manifest.containsKey(field))
        }
    }

    @Test
    fun `schemaVersion matches the schema's own pattern`() {
        val manifest = emitSampleManifest()
        val pattern = schema["properties"]!!.jsonObject["schemaVersion"]!!.jsonObject["pattern"]!!.jsonPrimitive.content
        val regex = Regex(pattern)

        val value = manifest["schemaVersion"]!!.jsonPrimitive.content
        assertTrue("schemaVersion '$value' does not match schema pattern '$pattern'", regex.matches(value))
    }

    @Test
    fun `exportId and exportedAt are present, non-empty, and exportedAt is a real date-time`() {
        val manifest = emitSampleManifest()

        val exportId = manifest["exportId"]!!.jsonPrimitive.content
        assertTrue(exportId.isNotBlank())

        val exportedAt = manifest["exportedAt"]!!.jsonPrimitive.content
        assertDateTime(exportedAt)
    }

    @Test
    fun `producer and projectRef carry every field their schema defs require`() {
        val manifest = emitSampleManifest()
        val defs = schema["\$defs"]!!.jsonObject

        val producerRequired = defs["CsAppProducerRef"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        val producer = manifest["producer"]!!.jsonObject
        producerRequired.forEach { field -> assertTrue("producer missing '$field'", producer.containsKey(field)) }
        assertEquals("csapp", producer["app"]!!.jsonPrimitive.content)
        assertTrue(producer["version"]!!.jsonPrimitive.content.isNotBlank())

        val projectRequired = defs["CsAppProjectRef"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        val projectRef = manifest["projectRef"]!!.jsonObject
        projectRequired.forEach { field -> assertTrue("projectRef missing '$field'", projectRef.containsKey(field)) }
        assertTrue(projectRef["externalId"]!!.jsonPrimitive.content.isNotBlank())
    }

    // -- issues[] --------------------------------------------------------------------------------

    @Test
    fun `every issue has every schema-required CsAppIssue field, non-empty where minLength 1 applies`() {
        val manifest = emitSampleManifest()
        val defs = schema["\$defs"]!!.jsonObject
        val issueDef = defs["CsAppIssue"]!!.jsonObject
        val required = issueDef["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        val minLength1Fields = issueDef["properties"]!!.jsonObject.entries
            .filter { (_, def) -> def.jsonObject["minLength"]?.jsonPrimitive?.content?.toIntOrNull() == 1 }
            .map { it.key }

        val issues = manifest["issues"]!!.jsonArray
        assertTrue("sample export should carry issues", issues.isNotEmpty())

        issues.forEach { issueElement ->
            val issue = issueElement.jsonObject
            required.forEach { field -> assertTrue("issue missing required field '$field'", issue.containsKey(field)) }
            minLength1Fields.forEach { field ->
                val value = issue[field]?.jsonPrimitive?.content
                assertTrue("issue field '$field' must be non-empty (minLength 1)", !value.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `every issue's severity is one of the schema's closed IssueSeverity enum`() {
        val manifest = emitSampleManifest()
        val allowedSeverities = schema["\$defs"]!!.jsonObject["IssueSeverity"]!!.jsonObject["enum"]!!
            .jsonArray.map { it.jsonPrimitive.content }

        manifest["issues"]!!.jsonArray.forEach { issue ->
            val severity = issue.jsonObject["severity"]!!.jsonPrimitive.content
            assertTrue("severity '$severity' not in schema enum $allowedSeverities", severity in allowedSeverities)
        }
    }

    @Test
    fun `every issue's occurredAt and updatedAt are real date-times`() {
        val manifest = emitSampleManifest()
        manifest["issues"]!!.jsonArray.forEach { issue ->
            assertDateTime(issue.jsonObject["occurredAt"]!!.jsonPrimitive.content)
            assertDateTime(issue.jsonObject["updatedAt"]!!.jsonPrimitive.content)
        }
    }

    // -- field-for-field shape check against core's own baseline fixture -----------------------

    @Test
    fun `producer and projectRef key sets match core's baseline fixture field-for-field`() {
        val manifest = emitSampleManifest()

        assertEquals(
            baselineFixture["producer"]!!.jsonObject.keys,
            manifest["producer"]!!.jsonObject.keys,
        )
        assertEquals(
            baselineFixture["projectRef"]!!.jsonObject.keys,
            manifest["projectRef"]!!.jsonObject.keys,
        )
    }

    @Test
    fun `an issue's key set matches core's baseline fixture issue key set field-for-field`() {
        val manifest = emitSampleManifest()
        val fixtureIssueKeys = (baselineFixture["issues"]!! as JsonArray).first().jsonObject.keys
        val ourIssueKeys = manifest["issues"]!!.jsonArray.first().jsonObject.keys

        assertEquals(fixtureIssueKeys, ourIssueKeys)
    }

    @Test
    fun `top-level key set matches the baseline fixture except the optional unknownFields bag we don't emit`() {
        val manifest = emitSampleManifest()
        val fixtureKeys = baselineFixture.keys
        val ourKeys = manifest.keys

        // unknownFields is optional (not in the schema's top-level "required" list) and exists
        // to preserve a *decoder's* passthrough bag; CSApp only ever encodes, so it never has one
        // to preserve. Everything else must match exactly.
        assertEquals(fixtureKeys - "unknownFields", ourKeys)
    }

    private fun assertDateTime(value: String) {
        try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            fail("'$value' is not a valid RFC3339/ISO-8601 date-time: ${e.message}")
        }
    }
}

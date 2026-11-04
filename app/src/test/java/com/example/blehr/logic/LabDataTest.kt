package com.example.blehr.logic

import com.example.blehr.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LabDataTest {
    private val action = ActionItem("water", "", "接杯水", 1, ActionCategory.FUEL, "站起来")
    private fun session() = ActionSession.start(action, EnergyBand.LOW, 80, 1_000_000, 100_000, 2)
    private fun backup(lab: LabData = LabData(listOf(action))) = BackupData(lab,
        BackupCodec.allowed.mapValues { emptyMap() }, 1_000_000)
    private fun reject(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun `background time is computed from deadline instead of ticks`() {
        assertEquals(30, session().remaining(1_030_000, 130_000, 2))
    }
    @Test fun `same boot ignores manual wall clock changes`() {
        assertEquals(30, session().remaining(2_030_000, 130_000, 2))
    }
    @Test fun `expired session cannot have negative remaining time`() {
        assertEquals(0, session().remaining(1_100_000, 200_000, 2))
    }
    @Test fun `reboot uses wall deadline and caps backwards clock`() {
        assertEquals(20, session().remaining(1_040_000, 1000, 3))
        assertEquals(60, session().remaining(1, 1000, 3))
        assertEquals(60, session().copy(deadline = Long.MAX_VALUE).remaining(1, 1000, 3))
    }
    @Test fun `pause survives elapsed time and resume uses remaining duration`() {
        val paused = session().pause(1_020_000, 120_000, 2)
        assertEquals(40, paused.remaining(2_000_000, 1_100_000, 2))
        val resumed = paused.resume(2_000_000, 1_100_000, 2)
        assertEquals(39, resumed.remaining(2_001_000, 1_101_000, 2))
        val corrected = paused.resume(100_000, 1_100_000, 2)
        assertEquals(corrected, LabCodec.session(LabCodec.sessionJson(corrected)))
    }
    @Test fun `pairing starts with actual remaining time`() {
        val s = ActionSession.start(action, EnergyBand.HIGH, null, 1_000_000, 100_000, 2, 970_000)
        assertTrue(s.paired)
        assertEquals(30, s.remaining(1_000_000, 100_000, 2))
    }
    @Test fun `feedback records original band heart rate and action snapshot`() {
        val s = session().copy(phase = SessionPhase.FEEDBACK)
        val result = LabData(listOf(action.copy(title = "改过的名字")), session = s).withFeedback(4, 75, 1_060_000)
        assertEquals(EnergyBand.LOW, result.records.single().band)
        assertEquals(80, result.records.single().hrBefore)
        assertEquals("接杯水", result.records.single().actionSnapshot!!.title)
        assertNull(result.session)
    }
    @Test fun `double feedback submission is idempotent`() {
        val d = LabData(listOf(action), session = session().copy(phase = SessionPhase.FEEDBACK))
            .withFeedback(4, 75, 1_060_000)
        assertEquals(d, d.withFeedback(5, 70, 1_061_000))
    }
    @Test fun `feedback cannot be saved before completing the action`() {
        reject { LabData(listOf(action), session = session()).withFeedback(4, 75, 1_000_000) }
    }
    @Test fun `snapshot round trip preserves paused session and archives`() {
        val d = LabData(listOf(action), favorites = setOf("water"), archived = setOf("water"),
            session = session().pause(1_020_000, 120_000, 2))
        assertEquals(d, LabCodec.decode(JSONObject(LabCodec.encode(d).toString())))
    }
    @Test fun `backup preserves Unicode profile chat and preferences`() {
        val b = backup().let { it.copy(preferences = it.preferences + ("huixue_chat" to mapOf("turns_v1" to "主人\n今天试试看"))) }
        assertEquals(b, BackupCodec.decode("\uFEFF" + BackupCodec.encode(b)))
    }
    @Test fun `credentials and arbitrary preference stores are rejected`() {
        val b = backup()
        reject { BackupCodec.decode(BackupCodec.encode(b.copy(preferences = b.preferences + ("huixue_llm" to mapOf("key" to "example"))))) }
        reject { BackupCodec.decode(BackupCodec.encode(b.copy(preferences = b.preferences + ("huixue_chat" to mapOf("apiKey" to "example"))))) }
    }
    @Test fun `unsupported backup versions and platforms do not decode`() {
        val o = JSONObject(BackupCodec.encode(backup()))
        reject { BackupCodec.decode(o.put("version", 2).toString()) }
        reject { BackupCodec.decode(o.put("version", 1).put("platform", "web").toString()) }
    }
    @Test fun `incomplete preferences and invalid setting types are rejected`() {
        val b = backup()
        reject { BackupCodec.decode(BackupCodec.encode(b.copy(preferences = emptyMap()))) }
        reject { BackupCodec.decode(BackupCodec.encode(b.copy(preferences = b.preferences + ("huixue_voice" to mapOf("enabled_v1" to "true"))))) }
    }
    @Test fun `invalid duration duplicate actions and unknown favorite ids are rejected`() {
        reject { LabCodec.decode(LabCodec.encode(LabData(listOf(action.copy(minutes = 0))))) }
        reject { LabCodec.decode(LabCodec.encode(LabData(listOf(action, action)))) }
        reject { LabCodec.decode(LabCodec.encode(LabData(listOf(action), favorites = setOf("missing")))) }
    }
    @Test fun `invalid ratings duplicate session ids and reused active session are rejected`() {
        val r = ExperimentRecord(action.id, EnergyBand.MID, null, null, 4, 1_000_000, "session", action)
        reject { LabCodec.decode(LabCodec.encode(LabData(listOf(action), listOf(r.copy(rating = 6))))) }
        reject { LabCodec.decode(LabCodec.encode(LabData(listOf(action), listOf(r, r)))) }
        reject { LabCodec.decode(LabCodec.encode(LabData(listOf(action), listOf(r), session = session().copy(id = "session")))) }
    }
    @Test fun `historical export and category survive later edits`() {
        val old = action.copy(title = "旧标题,一杯水")
        val changed = action.copy(title = "新标题", category = ActionCategory.MOVE)
        val r = ExperimentRecord(action.id, EnergyBand.MID, null, null, 4, 1_000_000, "session", old)
        assertTrue(RecordCsv.toCsv(listOf(r), listOf(changed)).contains("\"旧标题,一杯水\""))
        assertEquals(1, ExperimentReport.build(listOf(r), listOf(changed)).byCategory.first { it.label == ActionCategory.FUEL.label }.n)
    }
    @Test fun `oversized and truncated backup rejected without a partial result`() {
        reject { BackupCodec.decode(" ".repeat(LabCodec.MAX_BYTES + 1)) }
        reject { BackupCodec.decode(BackupCodec.encode(backup()).dropLast(12)) }
    }
}

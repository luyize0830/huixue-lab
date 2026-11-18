package com.example.blehr.logic

import com.example.blehr.model.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationLogTest {
    private val action = ActionItem("water", "", "喝水", 1, ActionCategory.FUEL, "接水")
    private val round = RecommendationRound("round", 1000, listOf(action.id), EnergyBand.MID, false)
    private val session = ActionSession.start(action, EnergyBand.MID, 80, 2000, 2000, 1)

    @Test fun recompositionDoesNotIncreaseImpressions() {
        val once = RecommendationLog.show(emptyList(), round)
        assertEquals(once, RecommendationLog.show(once, round))
    }
    @Test fun selectedRoundCannotBecomeSkippedWhenPageDisposes() {
        val selected = RecommendationLog.select(listOf(round), round, session, 2000)
        assertEquals(selected, RecommendationLog.skip(selected, round.id, 3000))
    }
    @Test fun demoAndDirectPoolFeedbackDoNotInflateRealMetrics() {
        val demo = round.copy(id = "demo", demo = true)
        val rounds = RecommendationLog.select(listOf(round, demo), demo, session, 2000)
        val records = listOf(ExperimentRecord(action.id, EnergyBand.MID, 80, 75, 5, 4000, session.id, action))
        val metrics = RecommendationLog.metrics(rounds, records)
        assertEquals(1, metrics.shown)
        assertEquals(0, metrics.selected)
        assertEquals(1, metrics.demo)
        assertNull(metrics.feedbackRate)
    }
    @Test fun acceptedAndFeedbackDenominatorsAreDifferent() {
        val other = round.copy(id = "other")
        val rounds = RecommendationLog.select(listOf(round, other), round, session, 2000)
        val data = LabData(listOf(action), session = session.copy(phase = SessionPhase.FEEDBACK), recommendations = rounds)
            .withFeedback(4, 75, 4000)
        val metrics = RecommendationLog.metrics(data.recommendations, data.records)
        assertEquals(50.0, metrics.acceptance!!, 0.001)
        assertEquals(100.0, metrics.feedbackRate!!, 0.001)
    }
    @Test fun oldSnapshotLoadsWithoutFabricatedHistory() {
        val json = LabCodec.encode(LabData(listOf(action))).also { it.remove("recommendations") }
        assertTrue(LabCodec.decode(json).recommendations.isEmpty())
    }
    @Test fun snapshotPreservesSkippedAndSelectedRounds() {
        val other = round.copy(id = "other")
        val rounds = RecommendationLog.skip(RecommendationLog.select(listOf(round, other), round, session, 2000), other.id, 3000)
        val data = LabData(listOf(action), session = session, recommendations = rounds)
        assertEquals(data, LabCodec.decode(LabCodec.encode(data)))
    }
    @Test fun selectionMustReferToDisplayedCandidates() {
        assertTrue(runCatching { RecommendationLog.select(listOf(round), round, session.copy(action = action.copy(id = "unknown")), 2000) }.isFailure)
        val json = RecommendationLog.json(round).put("outcome", "SELECTED")
        assertTrue(runCatching { RecommendationLog.decode(json) }.isFailure)
    }
    @Test fun csvRetainsSourceAndUnresolvedOutcome() {
        val csv = RecommendationLog.csv(listOf(round.copy(demo = true)), emptyList())
        assertTrue(csv.contains("\"DEMO\""))
        assertTrue(csv.contains("\"SHOWN\""))
        assertTrue(csv.endsWith("\"false\""))
    }
}

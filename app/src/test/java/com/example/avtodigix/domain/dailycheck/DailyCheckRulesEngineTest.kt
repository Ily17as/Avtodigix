package com.example.avtodigix.domain.dailycheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckRulesEngineTest {

    @Test
    fun `evaluate should calculate all cards and overall score for boundary values`() {
        val result = DailyCheckRulesEngine.evaluate(
            current = DailyCheckInput(
                batteryStartVoltage = 12.2,
                engineIdleRpm = 650,
                warmupDurationSeconds = 600,
                chargingVoltage = 13.6,
                activeDtcs = emptySet()
            )
        )

        assertEquals(5, result.cards.size)
        assertTrue(result.cards.all { it.status == DailyCheckCardStatus.OK })
        assertEquals(100, result.overallScore)
        assertEquals(OverallStatus.GOOD, result.overallStatus)
        assertEquals(DriveStatus.OK, result.driveStatus)
    }

    @Test
    fun `evaluate should apply section 14 penalties and critical status`() {
        val result = DailyCheckRulesEngine.evaluate(
            current = DailyCheckInput(
                batteryStartVoltage = 11.6,
                engineIdleRpm = 1100,
                warmupDurationSeconds = 1000,
                chargingVoltage = 15.2,
                activeDtcs = setOf("P0300", "P0420")
            )
        )

        assertEquals(12, result.overallScore)
        assertEquals(OverallStatus.CRITICAL, result.overallStatus)
        assertEquals(DriveStatus.NEW_DTC, result.driveStatus)
        assertEquals(setOf("P0300", "P0420"), result.newDtcs)
    }

    @Test
    fun `evaluate should set drive status to insufficient data when partial data provided`() {
        val result = DailyCheckRulesEngine.evaluate(
            current = DailyCheckInput(
                batteryStartVoltage = null,
                engineIdleRpm = 800,
                warmupDurationSeconds = null,
                chargingVoltage = 14.1,
                activeDtcs = emptySet()
            )
        )

        assertEquals(92, result.overallScore)
        assertEquals(DriveStatus.INSUFFICIENT_DATA, result.driveStatus)
        assertEquals(2, result.cards.count { it.status == DailyCheckCardStatus.INSUFFICIENT_DATA })
    }

    @Test
    fun `evaluate should generate top changes with required priority and cap at three`() {
        val previous = DailyCheckInput(
            batteryStartVoltage = 12.1,
            engineIdleRpm = 930,
            warmupDurationSeconds = 650,
            chargingVoltage = 14.7,
            activeDtcs = setOf("P0130")
        )
        val current = DailyCheckInput(
            batteryStartVoltage = 11.5,
            engineIdleRpm = 980,
            warmupDurationSeconds = 500,
            chargingVoltage = 14.9,
            activeDtcs = setOf("P0130", "P0300")
        )

        val result = DailyCheckRulesEngine.evaluate(current = current, previous = previous)

        assertEquals(3, result.topChanges.size)
        assertEquals(TopChangeType.NEW_ERROR, result.topChanges[0].type)
        assertEquals(DailyCheckCardId.BATTERY_START, result.topChanges[0].cardId)

        assertEquals(TopChangeType.NEW_ERROR, result.topChanges[1].type)
        assertEquals(DailyCheckCardId.OBD_ERRORS, result.topChanges[1].cardId)

        assertEquals(TopChangeType.REPEATED_WORSENING, result.topChanges[2].type)
        assertEquals(DailyCheckCardId.ENGINE_IDLE, result.topChanges[2].cardId)
    }

    @Test
    fun `evaluate should return no top changes for first check`() {
        val result = DailyCheckRulesEngine.evaluate(
            current = DailyCheckInput(
                batteryStartVoltage = 12.4,
                engineIdleRpm = 760,
                warmupDurationSeconds = 520,
                chargingVoltage = 14.2,
                activeDtcs = emptySet()
            ),
            previous = null
        )

        assertTrue(result.topChanges.isEmpty())
    }

    @Test
    fun `evaluate should detect improvement change`() {
        val previous = DailyCheckInput(
            batteryStartVoltage = 11.6,
            engineIdleRpm = 1050,
            warmupDurationSeconds = 950,
            chargingVoltage = 15.1,
            activeDtcs = setOf("P0420")
        )
        val current = DailyCheckInput(
            batteryStartVoltage = 12.4,
            engineIdleRpm = 760,
            warmupDurationSeconds = 520,
            chargingVoltage = 14.2,
            activeDtcs = emptySet()
        )

        val result = DailyCheckRulesEngine.evaluate(current = current, previous = previous)

        assertTrue(result.topChanges.any { it.type == TopChangeType.IMPROVEMENT })
        assertEquals(DriveStatus.ATTENTION, result.driveStatus)
    }
}

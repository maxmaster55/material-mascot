/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.maxmaster.materialmascot.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotStageTest {

    @Test
    fun stage_stays_claimed_until_every_owner_exits() {
        val first = BotStage.enter()
        val second = BotStage.enter()

        BotStage.exit(first)
        assertTrue(BotStage.bigCompanionOnStage)

        BotStage.exit(second)
        assertFalse(BotStage.bigCompanionOnStage)
    }

    @Test
    fun stale_double_exit_does_not_clear_a_new_owner() {
        val stale = BotStage.enter()
        BotStage.exit(stale)
        val current = BotStage.enter()

        BotStage.exit(stale)
        assertTrue(BotStage.bigCompanionOnStage)

        BotStage.exit(current)
        assertFalse(BotStage.bigCompanionOnStage)
    }
}

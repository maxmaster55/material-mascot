/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Skald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.maxmaster.materialmascot.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A real operation state any number of bots can mirror at once. */
enum class MascotStatus { IDLE, WORKING, SLOW, SUCCESS, ERROR }

/** What the bus is currently busy with; a free-form tag owned by the host app. */
data class MascotBusState(
    val status: MascotStatus = MascotStatus.IDLE,
    val tag: String? = null
)

/**
 * Process-wide status bus for work that can outlive a screen.
 *
 * Hosts hand out tokens from [begin] and must finish them when real work
 * completes. Operations are reference-counted, so one short job cannot hide
 * another that is still running. Long work becomes [MascotStatus.SLOW] after
 * [SLOW_AFTER_MS]; terminal states are deliberately transient.
 *
 * Every [LiveBot] reading [LocalMascotStatus] mirrors this state — one begin()
 * call moves ALL companion instances on screen at once.
 */
class MascotStatusBus {
    private data class Operation(
        val tag: String,
        val startedAt: Long,
        var status: MascotStatus = MascotStatus.WORKING,
        var endedAt: Long? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val operations = LinkedHashMap<Long, Operation>()
    private val timers = mutableMapOf<Long, Job>()
    private var nextToken = 0L
    private val _state = MutableStateFlow(MascotBusState())
    val state: StateFlow<MascotBusState> = _state.asStateFlow()

    fun begin(tag: String): Long = synchronized(lock) {
        val token = ++nextToken
        operations[token] = Operation(tag, System.currentTimeMillis())
        timers[token] = scope.launch {
            delay(SLOW_AFTER_MS)
            synchronized(lock) {
                val current = operations[token]
                if (current?.status == MascotStatus.WORKING) {
                    current.status = MascotStatus.SLOW
                    publishLocked()
                }
            }
        }
        publishLocked()
        token
    }

    fun success(token: Long) = finish(token, MascotStatus.SUCCESS)

    fun error(token: Long) = finish(token, MascotStatus.ERROR)

    /** User cancellation is not an error and returns to idle when appropriate. */
    fun cancel(token: Long) = synchronized(lock) {
        operations.remove(token) ?: return@synchronized
        timers.remove(token)?.cancel()
        publishLocked()
    }

    private fun finish(token: Long, terminal: MascotStatus) {
        synchronized(lock) {
            val operation = operations[token] ?: return
            operation.status = terminal
            operation.endedAt = System.currentTimeMillis()
            timers.remove(token)?.cancel()
            timers[token] = scope.launch {
                delay(TERMINAL_MS)
                synchronized(lock) {
                    if (operations[token]?.status == terminal) {
                        operations.remove(token)
                        timers.remove(token)
                        publishLocked()
                    }
                }
            }
            publishLocked()
        }
    }

    /** ERROR > SUCCESS > SLOW > WORKING > IDLE, with newest work winning ties. */
    private fun publishLocked() {
        val chosen = operations.entries
            .map { it.key to it.value }
            .sortedWith(
                compareByDescending<Pair<Long, Operation>> { priority(it.second.status) }
                    .thenByDescending { it.second.endedAt ?: it.second.startedAt }
            )
            .firstOrNull()?.second
        _state.value = chosen?.let { MascotBusState(it.status, it.tag) }
            ?: MascotBusState()
    }

    private fun priority(status: MascotStatus): Int = when (status) {
        MascotStatus.ERROR -> 5
        MascotStatus.SUCCESS -> 4
        MascotStatus.SLOW -> 3
        MascotStatus.WORKING -> 2
        MascotStatus.IDLE -> 1
    }

    private companion object {
        const val SLOW_AFTER_MS = 10_000L
        const val TERMINAL_MS = 3_500L
    }
}

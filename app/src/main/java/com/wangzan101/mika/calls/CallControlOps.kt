package com.wangzan101.mika.calls

import com.wangzan101.mika.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import com.wangzan101.mika.calls.CallRuntime.Tracked
import com.wangzan101.mika.calls.CallRuntime.Work
import com.wangzan101.mika.calls.CallRuntime.Companion.CALL_CHANGED

// 电话运行时:拨号、接听、按键、挂断与接管
suspend fun CallRuntime.dial(number: String, simId: String? = null, task: CallTaskBrief? = null): CallActionResult = onMain {
    val taskSnapshot = try {
        task?.validatedSnapshot()
    } catch (e: IllegalArgumentException) {
        return@onMain report(e.message ?: "The instructions for this call are invalid")
    }
    prerequisite()?.let { return@onMain report(it) }
    if (lockedDial != null) return@onMain report("Cancel the scheduled locked-screen dial first")
    val normalized = CallPolicy.outgoingNumber(number) ?: return@onMain report("Only regular numbers are supported, not emergency numbers, short codes or control codes")
    if (isEmergency(normalized) != false) return@onMain report("Emergency numbers and numbers that cannot be confirmed safe cannot be dialed")
    if (liveCalls().isNotEmpty() || work != null || managedCallPresent() != false) {
        return@onMain report("A call is in progress or idle state cannot be confirmed; not dialed")
    }
    readSims()
    prerequisite()?.let { return@onMain report(it) }
    if (lockedDial != null) return@onMain report("Cancel the scheduled locked-screen dial first")
    if (liveCalls().isNotEmpty() || work != null || managedCallPresent() != false) {
        return@onMain report("A call is in progress or idle state cannot be confirmed; not dialed")
    }
    val config = settings.state.value
    val picked = CallPolicy.chooseOutgoingSim(sims.map { it.info }, simId, config.defaultOutgoingSimId)
        ?: return@onMain report(when {
            simId == null && config.defaultOutgoingSimId != null -> "The saved default dialing SIM is no longer available; please choose again"
            sims.isEmpty() -> "No SIM available for cellular calls"
            else -> "Choose a valid SIM, or explicitly choose the system default SIM"
        })
    val sim = sims.single { it.info.id == picked.id }
    val attempt = Work(CallDirection.OUTGOING, config, requestedNumber = normalized, sim = sim, task = taskSnapshot)
    startWork(attempt)
    awaitResult(attempt)
}

suspend fun CallRuntime.answer(expectedCallId: String? = null): CallActionResult = onMain {
    prerequisite()?.let { return@onMain report(it) }
    val call = liveCalls().singleOrNull()
    if (expectedCallId != null && call?.id != expectedCallId) return@onMain report(CALL_CHANGED)
    if (call == null || call.direction != CallDirection.INCOMING || call.phase != CallPhase.RINGING ||
        call.suppressed || !carrierCall(call.call)) {
        return@onMain report("No single cellular incoming call to answer")
    }
    work?.let {
        if (it.track === call && it.direction == CallDirection.INCOMING && it.stopReason.value == null) {
            // An explicit answer for this exact call takes priority over a scheduled answer.
            if (!it.answerSent) {
                it.manualAnswerRequested = true
                it.autoDelayEndsAt = null
                it.skipAutoDelay.complete(Unit)
                detail = app.getString(R.string.calls_notification_answer_preparing)
                publish()
            }
            return@onMain awaitResult(it)
        }
        return@onMain report("An AI call is already in progress")
    }
    val attempt = Work(CallDirection.INCOMING, settings.state.value, track = call, expectedCallId = expectedCallId)
    startWork(attempt)
    awaitResult(attempt)
}

/** Explicitly bound to the displayed carrier call; remains usable after AI handoff. */
suspend fun CallRuntime.sendDtmf(expectedCallId: String, digits: String): CallActionResult = onMain {
    val target = liveCalls().singleOrNull()?.takeIf { it.id == expectedCallId }
        ?: return@onMain fail(CALL_CHANGED)
    val result = sendCallDtmf(target, digits)
    CallActionResult(result.status == "submitted", dtmfMessage(result))
}

internal suspend fun CallRuntime.sendCallDtmf(target: Tracked, digits: String, owner: Work? = null): CallDtmfResult {
    fun owned(): Boolean = settings.state.value.enabled && tracked[target.call] === target &&
        target.phase == CallPhase.ACTIVE && liveCalls().singleOrNull() === target &&
        target.service.tracks(target.call) && carrierCall(target.call) &&
        (owner == null || (work === owner && owner.track === target && owner.bridging &&
            owner.stopReason.value == null && !target.suppressed && sameActiveMuteTarget(owner, target)))
    val result = try {
        dtmf.send(digits, stillOwned = ::owned,
            play = { digit -> target.call.playDtmfTone(digit) },
            stop = { target.call.stopDtmfTone() },
            budget = if (owner != null) target.dtmfBudget else null)
    } catch (cancelled: CancellationException) {
        recordDtmfStatus(target, owner, "Key presses stopped")
        throw cancelled
    }
    recordDtmfStatus(target, owner, dtmfMessage(result))
    return result
}

internal fun CallRuntime.recordDtmfStatus(target: Tracked, owner: Work?, message: String) {
    // Deliberately keep the keys out of transcript, history, notifications and logs.
    owner?.let { appendTranscript(it.transcript, "system", message) }
    if (tracked[target.call] === target && liveCalls().singleOrNull() === target) {
        if (liveTranscriptCallId == target.id) appendTranscript(liveTranscript, "system", message)
        if (target.phase == CallPhase.ACTIVE) detail = message
        publish()
    }
}

internal fun CallRuntime.dtmfMessage(result: CallDtmfResult): String = when (result.status) {
    "submitted" -> "Sent ${result.sentDigits} key presses; waiting for the other side"
    "partial" -> "Sent ${result.sentDigits}/${result.requestedDigits} key presses; sending was interrupted"
    "invalid_digits" -> "Enter 1 to 32 keys: 0–9, * and # only"
    "busy" -> "Keys are being sent; try again shortly"
    "budget_exceeded" -> "The AI reached its key press limit for this call; take over to use the keypad"
    "unavailable" -> "The call or AI control changed; key presses stopped"
    else -> "Key presses could not be completed; wait for the other side before trying again"
}

suspend fun CallRuntime.hangup(expectedCallId: String? = null): CallActionResult = onMain {
    if (!settings.state.value.enabled || !armed) return@onMain report("AI calling is not ready yet")
    val call = liveCalls().singleOrNull()
    if (expectedCallId != null && call?.id != expectedCallId) return@onMain report(CALL_CHANGED)
    if (call == null || call.phase == CallPhase.DISCONNECTING || !carrierCall(call.call)) {
        return@onMain report("No single cellular call to hang up")
    }
    // Shared by local and validated remote hangup; stop keys before waiting for AI cleanup.
    dtmf.cancelPending()
    call.suppressed = true
    val active = cancelWork("Hung up by user")
    val cleaned = awaitCleanup(active)
    val endedMessage = if (cleaned) "Android confirmed the call ended" else "Android confirmed the call ended; audio restore is not confirmed"
    if (confirmedEnded(call)) return@onMain ok(endedMessage)
    if (!isCurrentCall(call, expectedCallId) || call.phase == CallPhase.DISCONNECTING) return@onMain report(CALL_CHANGED)
    if (runCatching {
            if (call.phase == CallPhase.RINGING) call.call.reject(false, null) else call.call.disconnect()
        }.isFailure) return@onMain report("Android could not send the hang-up request")
    val ended = waitFor(CallPolicy.CONTROL_TIMEOUT_MS) { confirmedEnded(call) } || confirmedEnded(call)
    if (ended) ok(endedMessage) else report("Android has not confirmed the call ended; check the system phone screen")
}

suspend fun CallRuntime.cancelAutoAnswer(expectedCallId: String? = null): CallActionResult = onMain {
    val current = work
    val target = current?.track
    if (current == null || target == null || !isPendingAutoAnswer(current, target) ||
        !isCurrentCall(target, expectedCallId)) {
        return@onMain fail("No auto-answer to cancel for this call")
    }
    val active = cancelWork("AI answering canceled for this call")
    if (!awaitCleanup(active)) return@onMain fail("AI answering canceled; cleanup is not confirmed")
    if (isCurrentCall(target, expectedCallId) && target.phase == CallPhase.RINGING) ok("AI answering canceled; the phone keeps ringing")
    else ok("AI answering canceled for this call")
}

suspend fun CallRuntime.takeover(): CallActionResult = takeover(null)

internal suspend fun CallRuntime.takeover(callId: String?): CallActionResult = onMain {
    val current = work
    if (current == null || (callId != null && callId != current.id)) return@onMain fail("This AI call has ended")
    dtmf.cancelPending()
    current.track?.suppressed = true
    val active = cancelWork(if (current.pendingRing) "AI answering canceled for this call" else "AI stopped; call handed to the system dialer")
    if (awaitCleanup(active)) ok(detail ?: "AI stopped")
    else report("AI stopped; call audio restore is not confirmed. Check the system phone screen")
}

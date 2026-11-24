package com.wangzan101.mika.calls

import android.app.KeyguardManager
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wangzan101.mika.calls.CallRuntime.CallProblem
import com.wangzan101.mika.calls.CallRuntime.PendingLockedDial
import com.wangzan101.mika.calls.CallRuntime.Work

// 电话运行时:锁屏时排队拨出
/** Takes effect only after the owner confirms this specific task while the app is visible. */
suspend fun CallRuntime.queueLockedDial(number: String, simId: String? = null, task: CallTaskBrief): CallActionResult = onMain {
    if (!interactiveForeground() || !userUnlocked()) return@onMain fail("Unlock the phone and confirm this dial on the Calls page")
    if (!secureDeviceLock()) return@onMain fail("Set a PIN, pattern or password lock on the phone first")
    if (lockedDial != null) return@onMain fail("A locked-screen dial is already scheduled; cancel it first")
    val snapshot = try { task.validatedSnapshot() } catch (e: IllegalArgumentException) {
        return@onMain fail(e.message ?: "The instructions for this call are invalid")
    }
    prerequisite()?.let { return@onMain fail(it) }
    val normalized = CallPolicy.outgoingNumber(number) ?: return@onMain fail("Only regular numbers are supported, not emergency numbers, short codes or control codes")
    if (isEmergency(normalized) != false) return@onMain fail("Emergency numbers and numbers that cannot be confirmed safe cannot be dialed")
    if (!lockedDialIdle()) return@onMain fail("A call is in progress or idle state cannot be confirmed; dial not scheduled")
    readSims()
    if (!withContext(Dispatchers.IO) { access.ready() }) return@onMain fail("Call management permission is unavailable")
    if (!interactiveForeground() || !userUnlocked() || !secureDeviceLock()) {
        return@onMain fail("Confirm the locked-screen dial while the phone is unlocked")
    }
    prerequisite()?.let { return@onMain fail(it) }
    if (lockedDial != null || !lockedDialIdle()) return@onMain fail("A dial is already scheduled or a call is in progress; dial not scheduled")
    val config = settings.state.value
    val picked = CallPolicy.chooseOutgoingSim(sims.map { it.info }, simId, config.defaultOutgoingSimId)
        ?: return@onMain fail(if (simId == null && config.defaultOutgoingSimId != null) "The saved default dialing SIM is no longer available; please choose again"
            else "Choose a valid SIM, or explicitly choose an available system default SIM")
    val sim = sims.single { it.info.id == picked.id }
    if (!CallStandbyService.notificationsAvailable(app, create = true)) return@onMain fail("Allow background standby notifications so the locked-screen dial can be canceled")
    val ticket = lockedDialGate.arm(normalized, picked.id, snapshot) ?: return@onMain fail("Could not schedule this locked-screen dial")
    val pending = PendingLockedDial(ticket, config, sim, settings.credentialsRevision.value)
    lockedDial = pending
    lockedDialDetail = "Locked-screen dial scheduled; lock the screen within 45 seconds"
    // A short one-shot lock keeps the confirmation window ticking as the screen turns off.
    // There is no indefinite standby wake lock and no attempt to wake or unlock the display.
    pending.wakeLock = runCatching {
        val power = checkNotNull(app.getSystemService(PowerManager::class.java))
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mika:LockedDial").apply {
            setReferenceCounted(false)
            acquire((ticket.expiresAtMs - SystemClock.elapsedRealtime() + 1_000L).coerceIn(1L, 46_000L))
        }
    }.getOrNull()
    if (pending.wakeLock == null) {
        finishLockedDial(pending, "Could not keep the locked-screen dial confirmation window; dial not scheduled")
        return@onMain fail(lockedDialDetail.orEmpty())
    }
    if (standbyService == null && !requestCallForegroundService(incoming = false)) {
        return@onMain fail(lockedDialDetail ?: "Background call service did not start")
    }
    pending.job = scope.launch(start = CoroutineStart.LAZY) { observeLockedDial(pending) }
    pending.job?.start()
    publish()
    if (lockedDial === pending) ok("Locked-screen dial scheduled; Android has not dialed yet")
    else fail(lockedDialDetail ?: "This locked-screen dial was not scheduled")
}

suspend fun CallRuntime.cancelLockedDial(requestId: String): CallActionResult = onMain {
    val pending = lockedDial?.takeIf { it.ticket.id == requestId }
        ?: return@onMain fail("This locked-screen dial has ended or was already submitted to the system")
    finishLockedDial(pending, "Locked-screen dial canceled")
    ok("Locked-screen dial canceled")
}

internal fun CallRuntime.secureDeviceLock(): Boolean =
    runCatching { app.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }.getOrDefault(false)

internal fun CallRuntime.lockedDialIdle(pending: PendingLockedDial? = null): Boolean =
    (work == null || pending != null && work?.lockedDialTicket === pending.ticket) &&
        liveCalls().isEmpty() && managedCallPresent() == false

internal fun CallRuntime.lockedDialEligible(pending: PendingLockedDial): Boolean = userUnlocked() && secureDeviceLock() &&
    (standbyService != null || standbyStarting) && settings.state.value.audioMode == pending.config.audioMode &&
    settings.credentialsRevision.value == pending.credentialsRevision &&
    prerequisite(ignoreWork = true) == null && CallStandbyService.notificationsAvailable(app) && lockedDialIdle(pending)

internal suspend fun CallRuntime.observeLockedDial(pending: PendingLockedDial) {
    while (lockedDial === pending) {
        val remaining = pending.ticket.expiresAtMs - SystemClock.elapsedRealtime()
        if (remaining <= 0L) {
            finishLockedDial(pending, "Locked-screen dial schedule expired; not dialed")
            return
        }
        val deviceLocked = keyguardLocked()
        val eligible = lockedDialEligible(pending)
        if (pending.claimed) {
            if (!deviceLocked || !eligible || standbyService == null) {
                finishLockedDial(pending, "Phone was unlocked or call conditions changed; locked-screen dial not performed")
                return
            }
        } else when (val decision = lockedDialGate.poll(pending.ticket, deviceLocked && standbyService != null, eligible)) {
            LockedDialGate.Decision.WaitingForLock -> Unit
            is LockedDialGate.Decision.Rejected -> {
                finishLockedDial(pending, if (decision.reason == LockedDialGate.Rejection.EXPIRED) "Locked-screen dial schedule expired; not dialed"
                    else "Call conditions changed; locked-screen dial not performed")
                return
            }
            is LockedDialGate.Decision.Claimed -> {
                pending.claimed = true
                lockedDialDetail = "Phone is locked; preparing to dial"
                val request = decision.request
                startWork(Work(CallDirection.OUTGOING, pending.config, requestedNumber = request.number,
                    sim = pending.sim, task = request.task, lockedDialTicket = pending.ticket))
            }
        }
        if (mutable.value.lockedDialRemainingSeconds != remainingSeconds(pending)) publish()
        delay(minOf(250L, remaining))
    }
}

internal fun CallRuntime.remainingSeconds(pending: PendingLockedDial): Int =
    ((pending.ticket.expiresAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L).div(1_000L).toInt()

internal fun CallRuntime.finishLockedDial(pending: PendingLockedDial, reason: String, cancelUnplacedWork: Boolean = true) {
    if (lockedDial !== pending) return
    lockedDial = null
    lockedDialGate.cancel(pending.ticket)
    pending.job?.cancel()
    runCatching { pending.wakeLock?.takeIf { it.isHeld }?.release() }
    pending.wakeLock = null
    lockedDialDetail = reason
    if (cancelUnplacedWork && work?.let { it.lockedDialTicket === pending.ticket && !it.placed } == true) cancelWork(reason)
    stopBackgroundStandbyIfUnneeded()
    publish()
}

internal fun CallRuntime.validateLockedDial(w: Work) {
    val ticket = w.lockedDialTicket ?: return
    val pending = lockedDial?.takeIf { it.ticket === ticket && it.claimed }
        ?: throw CallProblem("Locked-screen dial canceled; not dialed")
    when (lockedDialGate.submissionRejection(ticket) {
        standbyService != null && lockedDialEligible(pending) && keyguardLocked()
    }) {
        LockedDialGate.Rejection.EXPIRED -> throw CallProblem("Locked-screen dial schedule expired; not dialed")
        null -> Unit
        else -> throw CallProblem("Phone was unlocked or call conditions changed; locked-screen dial not performed")
    }
}

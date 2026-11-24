package com.wangzan101.mika.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallTelecomAccessTest {
    @Test fun recognizesExactShellPermissionDenialWithoutLeakingRawOutput() {
        val privateSuffix = "private-device-data"
        val output = """
            Exception occurred while executing 'set':
            java.lang.SecurityException: uid 2000 does not have android.permission.MANAGE_APP_OPS_MODES. $privateSuffix
        """.trimIndent()
        for (exitCode in listOf(255, 0)) {
            val result = CallTelecomCommandOutcome.classify(exitCode, output)
            assertEquals(CallTelecomCommandOutcome.SHELL_APP_OPS_DENIED, result)
            assertEquals(
                "The system blocks ADB from changing call management permission (MANAGE_APP_OPS_MODES missing), so phone companion cannot be enabled",
                result.appOpsFailureMessage,
            )
            assertFalse(result.appOpsFailureMessage.contains(privateSuffix))
        }
    }

    @Test fun successfulCommandWithDefaultReadbackReportsSystemRejection() {
        val result = CallTelecomCommandOutcome.classify(0, "")
        val readBack = CallPolicy.previousAppOpMode("No operations.\nDefault mode: default")
        assertEquals("Access was requested, but the system still reports call management as default; AI calling cannot be turned on",
            result.verificationFailure(readBack, false))
    }

    @Test fun defaultAppOpAcceptsAnEffectivePackageGrant() {
        assertEquals(null, CallTelecomCommandOutcome.OK.verificationFailure("default", true))
    }

    @Test fun failedReadbackIsNotReportedAsPermissionDenied() {
        assertEquals("The grant command ran, but call management access could not be read back; try again",
            CallTelecomCommandOutcome.OK.verificationFailure(null, false))
    }

    @Test fun allowedAppOpStillRequiresTelecomPermission() {
        assertEquals("Call management reads back as allowed, but the system call service is still not authorized; AI calling cannot be turned on",
            CallTelecomCommandOutcome.OK.verificationFailure("allow", false))
        assertEquals(null, CallTelecomCommandOutcome.OK.verificationFailure("allow", true))
    }

    @Test fun commandFailuresTakePriorityAndUnknownReadbackNeverLeaks() {
        for (outcome in listOf(CallTelecomCommandOutcome.FAILED, CallTelecomCommandOutcome.SHELL_APP_OPS_DENIED)) {
            assertEquals(outcome.appOpsFailureMessage, outcome.verificationFailure("allow", true))
        }
        assertEquals("The grant command ran, but call management access could not be read back; try again",
            CallTelecomCommandOutcome.OK.verificationFailure("private-device-data", false))
        for (mode in listOf("ignore", "deny", "errored", "foreground")) {
            assertEquals("Access was requested, but the system still reports call management as $mode; AI calling cannot be turned on",
                CallTelecomCommandOutcome.OK.verificationFailure(mode, true))
        }
    }

    @Test fun unknownFailuresDoNotSuggestShellAppOpsPermissionDenial() {
        listOf(
            "java.lang.SecurityException: uid 2000 does not have android.permission.GRANT_RUNTIME_PERMISSIONS.",
            "java.lang.SecurityException: uid 10123 does not have android.permission.MANAGE_APP_OPS_MODES.",
            "log: read MANAGE_APP_OPS_MODES",
            "Unknown command: set",
        ).forEach {
            val result = CallTelecomCommandOutcome.classify(255, it)
            assertEquals(CallTelecomCommandOutcome.FAILED, result)
            assertEquals("The system did not grant call management permission", result.appOpsFailureMessage)
        }
        assertEquals(CallTelecomCommandOutcome.OK, CallTelecomCommandOutcome.classify(0, ""))
    }
}

package com.example.finalproject_demo

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gives every Robolectric test its own applier for Compose state written outside a composition (09-30).
 *
 * Symptom (eval/results.md 09-29 night · 09-30): in one JVM, ShellFlowTest (or ScreenShotTest) → any
 * Robolectric test that writes Compose state outside a composition (even `mutableStateOf(0).value = 1`)
 * → a Compose screen test failed with "Compose did not get idle (60 s)", intermittently. It was hidden by
 * running every test class in a fresh JVM (`forkEvery = 1`).
 *
 * Cause: such writes are applied by compose-ui's `GlobalSnapshotManager`, which is started **once per JVM**
 * and keeps running from the test that started it, while Robolectric resets the main looper between tests.
 * In a later test its apply may never run, so the write stays pending and Compose never becomes idle.
 * Which step of the old manager stalls is not pinned down; what was measured:
 *  - flushing once before each test (`sendApplyNotifications`) — the order still failed
 *  - also re-arming the manager (`started`/`sent` reset) — the order passed, the full suite still failed once
 *    in `parentModeOnTabletPortrait`, which writes `d.s.stage` *before* `setContent`
 *  - also applying writes from the current test's own main looper (below) — full suite 231/231, twice
 *
 * Test-only: an app runs one looper for its whole life, so this cannot happen on a phone.
 * Relies on compose-ui internals (fields `started` · `sent`, checked on 1.7.0) — a rename throws here,
 * so an upgrade fails loudly instead of bringing the flaky order back.
 * Registered in `src/test/resources/META-INF/services/org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin`.
 */
class ComposeSnapshotFlush : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        val gsm = Class.forName("androidx.compose.ui.platform.GlobalSnapshotManager")
        fun flag(name: String) = gsm.getDeclaredField(name).apply { isAccessible = true }.get(null) as AtomicBoolean
        flag("sent").set(false)
        flag("started").set(false)
        Snapshot.sendApplyNotifications()

        observer?.dispose()
        val pending = AtomicBoolean(false)
        observer = Snapshot.registerGlobalWriteObserver {
            // The main looper can still be missing while Robolectric sets up — look it up at write time
            val looper = android.os.Looper.getMainLooper() ?: return@registerGlobalWriteObserver
            if (pending.compareAndSet(false, true)) android.os.Handler(looper).post { pending.set(false); Snapshot.sendApplyNotifications() }
        }
    }

    private companion object {
        var observer: ObserverHandle? = null
    }
}

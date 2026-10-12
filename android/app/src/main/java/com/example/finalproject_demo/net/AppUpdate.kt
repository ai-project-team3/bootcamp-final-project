package com.example.finalproject_demo.net

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play in-app update, immediate type (10-09 user) — when the store has a newer build, Google's full-screen update runs
 * before the app is used. A flexible update downloads in the background and asks for a restart later, which could
 * break into a picture book being made; the immediate one happens before anything starts.
 *
 * Only a build installed from Play (store or test track) gets an answer; a build installed with adb hears 「no update」.
 * Backing out of the update screen brings it back on the next resume — the app is used on the newest build.
 * A failed update (no space · network) lets the app open, so a broken store never locks the child out.
 */
class AppUpdate(private val activity: ComponentActivity) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private var failed = false
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> Log.i(TAG, "update accepted")
                Activity.RESULT_CANCELED -> Log.i(TAG, "update backed out — asked again on resume")
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> { failed = true; Log.w(TAG, "update failed — app opens") }
            }
        }

    /** Call from onResume — also resumes an update that was already running when the app came back */
    fun check() {
        if (failed) return
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val running = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            if (!running && !available) return@addOnSuccessListener
            Log.i(TAG, "newer build ${info.availableVersionCode()} — immediate update")
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        }.addOnFailureListener { Log.i(TAG, "no update check — ${it.message}") }
    }

    private companion object { const val TAG = "AppUpdate" }
}

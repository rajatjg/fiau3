package de.ffuf.in_app_update

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.annotation.NonNull
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import android.util.Log
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.UpdateAvailability
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

interface ActivityProvider {
    fun addActivityResultListener(callback: PluginRegistry.ActivityResultListener)
    fun activity(): Activity?
}

class InAppUpdatePlugin : FlutterPlugin, MethodCallHandler,
    PluginRegistry.ActivityResultListener, Application.ActivityLifecycleCallbacks, ActivityAware {

    companion object {
        private const val REQUEST_CODE_START_UPDATE = 1278
    }

    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.v("IN_APP_UPDATE", "onAttachedToEngine() called")
        channel = MethodChannel(
            flutterPluginBinding.binaryMessenger,
            "in_app_update"
        )
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Log.v("IN_APP_UPDATE", "onDetachedFromEngine() called")
        channel.setMethodCallHandler(null)
    }

    private var activityProvider: ActivityProvider? = null

    private var updateResult: Result? = null
    private var appUpdateType: Int? = null
    private var appUpdateInfo: AppUpdateInfo? = null
    private var appUpdateManager: AppUpdateManager? = null

    override fun onMethodCall(call: MethodCall, result: Result) {
        Log.v("IN_APP_UPDATE", "onMethodCall() called")
        when (call.method) {
            "checkForUpdate" -> checkForUpdate(result)
            "performImmediateUpdate" -> performImmediateUpdate(result)
            "startFlexibleUpdate" -> startFlexibleUpdate(result)
            "completeFlexibleUpdate" -> completeFlexibleUpdate(result)
            else -> result.notImplemented()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        Log.v("IN_APP_UPDATE", "onActivityResult()...... called")
        if (requestCode == REQUEST_CODE_START_UPDATE) {
            if (appUpdateType == AppUpdateType.IMMEDIATE) {
                if (resultCode == RESULT_CANCELED) {
                    updateResult?.error("USER_DENIED_UPDATE", resultCode.toString(), null)
                } else if (resultCode == RESULT_OK) {
                    updateResult?.success(null)
                } else if (resultCode == ActivityResult.RESULT_IN_APP_UPDATE_FAILED) {
                    updateResult?.error("IN_APP_UPDATE_FAILED", "Some other error prevented either the user from providing consent or the update to proceed.", null)
                }
                updateResult = null
                return true
            }else if (appUpdateType == AppUpdateType.FLEXIBLE) {
                if (resultCode == RESULT_CANCELED) {
                    updateResult?.error("USER_DENIED_UPDATE", resultCode.toString(), null)
                    updateResult = null
                }
                else if (resultCode == ActivityResult.RESULT_IN_APP_UPDATE_FAILED) {
                    updateResult?.error("IN_APP_UPDATE_FAILED", resultCode.toString(), null)
                    updateResult = null
                }
                return true
            }
        }
        return false
    }


    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        Log.v("IN_APP_UPDATE", "onAttachedToActivity() called")
        activityProvider = object : ActivityProvider {
            override fun addActivityResultListener(callback: PluginRegistry.ActivityResultListener) {
                activityPluginBinding.addActivityResultListener(callback)
            }

            override fun activity(): Activity? {
                return activityPluginBinding.activity
            }
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        Log.v("IN_APP_UPDATE", "onDetachedFromActivityForConfigChanges() called")
        activityProvider = null
    }

    override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
        Log.v("IN_APP_UPDATE", "onReattachedToActivityForConfigChanges() called")
        activityProvider = object : ActivityProvider {
            override fun addActivityResultListener(callback: PluginRegistry.ActivityResultListener) {
                activityPluginBinding.addActivityResultListener(callback)
            }

            override fun activity(): Activity? {
                return activityPluginBinding.activity
            }
        }
    }

    override fun onDetachedFromActivity() {
        Log.v("IN_APP_UPDATE", "onDetachedFromActivity() called")
        activityProvider = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.v("IN_APP_UPDATE", "onActivityCreated() called")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.v("IN_APP_UPDATE", "onActivityPaused() called")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.v("IN_APP_UPDATE", "onActivityStarted() called")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.v("IN_APP_UPDATE", "onActivityDestroyed() called")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.v("IN_APP_UPDATE", "onActivitySaveInstanceState() called")
    }

    override fun onActivityStopped(activity: Activity) {
        Log.v("IN_APP_UPDATE", "onActivityStopped() called")
    }

    override fun onActivityResumed(activity: Activity) {
        Log.v("IN_APP_UPDATE", "onActivityResumed()....... called")
        appUpdateManager
            ?.appUpdateInfo
            ?.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability()
                    == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    && appUpdateType == AppUpdateType.IMMEDIATE
                ) {
                    appUpdateManager?.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        REQUEST_CODE_START_UPDATE
                    )
                }
            }
    }

    private fun performImmediateUpdate(result: Result) = checkAppState(result) {
        Log.v("IN_APP_UPDATE", "performImmediateUpdate() called")
        appUpdateType = AppUpdateType.IMMEDIATE
        updateResult = result
        appUpdateManager?.startUpdateFlowForResult(
            appUpdateInfo!!,
            AppUpdateType.IMMEDIATE,
            activityProvider!!.activity()!!,
            REQUEST_CODE_START_UPDATE
        )
    }

    private fun checkAppState(result: Result, block: () -> Unit) {
        requireNotNull(appUpdateInfo) {
            result.error("REQUIRE_CHECK_FOR_UPDATE", "Call checkForUpdate first!", null)
        }
        requireNotNull(activityProvider?.activity()) {
            result.error("REQUIRE_FOREGROUND_ACTIVITY", "in_app_update requires a foreground activity", null)
        }
        requireNotNull(appUpdateManager) {
            result.error("REQUIRE_CHECK_FOR_UPDATE", "Call checkForUpdate first!", null)
        }
        block()
    }

    private fun startFlexibleUpdate(result: Result) = checkAppState(result) {
        Log.v("IN_APP_UPDATE", "startFlexibleUpdate(() called")
        appUpdateType = AppUpdateType.FLEXIBLE
        updateResult = result
        appUpdateManager?.startUpdateFlowForResult(
            appUpdateInfo!!,
            AppUpdateType.FLEXIBLE,
            activityProvider!!.activity()!!,
            REQUEST_CODE_START_UPDATE
        )
        appUpdateManager?.registerListener { state ->
        Log.v("IN_APP_UPDATE", "in listener after starting download")
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                updateResult?.success(null)
                updateResult = null
            } else if (state.installErrorCode() != InstallErrorCode.NO_ERROR) {
                updateResult?.error(
                    "Error during installation",
                    state.installErrorCode().toString(),
                    null
                )
                updateResult = null
            }
        }
    }

    private fun completeFlexibleUpdate(result: Result) = checkAppState(result) {
        Log.v("IN_APP_UPDATE", "completeFlexibleUpdate() called")
        appUpdateManager?.completeUpdate()
    }

    private fun checkForUpdate(result: Result) {
        Log.v("IN_APP_UPDATE", "checkForUpdate() called")
        requireNotNull(activityProvider?.activity()) {
            result.error("REQUIRE_FOREGROUND_ACTIVITY", "in_app_update requires a foreground activity", null)
        }

        activityProvider?.addActivityResultListener(this)
        activityProvider?.activity()?.application?.registerActivityLifecycleCallbacks(this)

        appUpdateManager = AppUpdateManagerFactory.create(activityProvider!!.activity()!!)

        // Returns an intent object that you use to check for an update.
        val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo

        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener { info ->
            appUpdateInfo = info
            result.success(
                mapOf(
                    "updateAvailability" to info.updateAvailability(),
                    "immediateAllowed" to info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                    "flexibleAllowed" to info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                    "availableVersionCode" to info.availableVersionCode(), //Nullable according to docs
                    "installStatus" to info.installStatus(),
                    "packageName" to info.packageName(),
                    "clientVersionStalenessDays" to info.clientVersionStalenessDays(), //Nullable according to docs
                    "updatePriority" to info.updatePriority()
                )
            )
        }
        appUpdateInfoTask.addOnFailureListener {
            result.error("TASK_FAILURE", it.message, null)
        }
    }
}

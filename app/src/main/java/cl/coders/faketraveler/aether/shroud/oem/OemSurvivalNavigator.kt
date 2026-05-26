package cl.coders.faketraveler.aether.shroud.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

/**
 * Aether v2 OEM survival navigator. Detects the device manufacturer and routes
 * to the correct battery / background-restriction settings screen so the user
 * can exempt FakeTraveler from aggressive OEM killing.
 *
 * All public functions return [Result] -- no `!!`, no `GlobalScope`, no `runBlocking`.
 */
object OemSurvivalNavigator {

    private const val TAG = "OemSurvivalNav"

    /** Supported OEM brands. Sub-brands resolve to the parent (POCO -> XIAOMI, etc.). */
    enum class OemBrand {
        XIAOMI, SAMSUNG, OPPO, VIVO, ONEPLUS, HUAWEI, UNKNOWN
    }

    // ── Brand detection ──────────────────────────────────────────────

    /** Lower-cased [Build.MANUFACTURER] for substring matching. */
    private fun manufacturer(): String =
        (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)

    /** Detect the [OemBrand] from [Build.MANUFACTURER]. Sub-brands and rebrands
     *  (POCO, Redmi, iQOO, HONOR, Realme) collapse into the parent bucket. */
    fun detectBrand(): OemBrand {
        val m = manufacturer()
        return when {
            "xiaomi" in m || "poco" in m || "redmi" in m -> OemBrand.XIAOMI
            "samsung" in m                                -> OemBrand.SAMSUNG
            "oppo" in m || "realme" in m                  -> OemBrand.OPPO
            "vivo" in m || "iqoo" in m                    -> OemBrand.VIVO
            "oneplus" in m                                -> OemBrand.ONEPLUS
            "huawei" in m || "honor" in m                 -> OemBrand.HUAWEI
            else                                          -> OemBrand.UNKNOWN
        }
    }

    // ── Battery optimisation status ──────────────────────────────────

    /** True when the app is already exempt from battery optimisation (API 23+).
     *  Returns `true` on pre-M devices where the concept does not exist. */
    fun isOptimizationExempt(context: Context): Result<Boolean> = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Result.success(true)
        } else {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            Result.success(pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
        }
    } catch (t: Throwable) {
        Log.e(TAG, "isOptimizationExempt failed", t)
        Result.failure(t)
    }

    // ── Request exemption (system dialog) ────────────────────────────

    /** Fire the system ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent.
     *  Only works on API 23+; silently succeeds on older devices. */
    fun requestExemption(context: Context): Result<Unit> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        Result.success(Unit)
    } catch (t: Throwable) {
        Log.e(TAG, "requestExemption failed", t)
        Result.failure(t)
    }

    // ── Navigate to OEM battery settings ─────────────────────────────

    /** Navigate to the brand-specific battery / autostart settings screen.
     *  Tries brand intents first, then falls back to the generic battery
     *  optimisation settings. Returns [Result.failure] only if every
     *  attempt throws. */
    fun navigateToBatterySettings(context: Context): Result<Unit> {
        val intents = brandIntents() + fallbackIntent()
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return Result.success(Unit)
            } catch (t: Throwable) {
                Log.w(TAG, "Intent failed: ${intent.component ?: intent.action}", t)
            }
        }
        return Result.failure(IllegalStateException("All OEM battery intents failed"))
    }

    // ── Private intent builders ──────────────────────────────────────

    /** Brand-specific intents per the task spec deep links. */
    private fun brandIntents(): List<Intent> = when (detectBrand()) {
        OemBrand.XIAOMI -> listOf(
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
        )
        OemBrand.SAMSUNG -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
        )
        OemBrand.OPPO -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                )
            )
        )
        OemBrand.VIVO -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            )
        )
        OemBrand.ONEPLUS -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            )
        )
        OemBrand.HUAWEI -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )
        )
        OemBrand.UNKNOWN -> emptyList()
    }

    /** Generic fallback: system battery optimisation settings. */
    private fun fallbackIntent(): List<Intent> = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )
}

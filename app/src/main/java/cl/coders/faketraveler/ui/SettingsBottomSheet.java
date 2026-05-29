package cl.coders.faketraveler.ui;

import static cl.coders.faketraveler.MainActivity.DECIMAL_FORMAT;
import static cl.coders.faketraveler.MainActivity.sharedPrefKey;
import static cl.coders.faketraveler.SharedPrefsUtil.getDouble;
import static cl.coders.faketraveler.SharedPrefsUtil.putDouble;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.Locale;

import cl.coders.faketraveler.FeatureFlag;
import cl.coders.faketraveler.MapProviderUtil;
import cl.coders.faketraveler.OemBatteryOptHelper;
import cl.coders.faketraveler.PackageInfoUtil;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.SharedPrefsUtil;
import cl.coders.faketraveler.util.Inputs;

/**
 * Bottom sheet that hosts every preference previously exposed by {@code MoreActivity}.
 *
 * <p>The wiring helpers are ported verbatim from MoreActivity with two changes only:
 * <ul>
 *   <li>Context source flips from {@code this} (Activity) to {@code requireContext()} /
 *       {@code requireActivity()} / the passed-in {@link View}.</li>
 *   <li>The 7-tap debug-console gate on {@code tv_AppVersion} is dropped — T10 rehomes
 *       the gate in the new AboutActivity, so the version footer here is read-only.</li>
 * </ul>
 *
 * <p>The current layout (bottom_sheet_settings.xml) is a placeholder that preserves every
 * view ID from the legacy activity_more layout so the ported Java keeps binding. T12
 * rebuilds the visual structure (drag handle, sliders, sections) from scratch per the
 * UI v2.0 brief.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    @NonNull
    private static final String TAG = SettingsBottomSheet.class.getSimpleName();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = requireContext().getApplicationContext();
        SharedPrefsUtil.migrateToV2(context);                                            // FIX-014
        final SharedPreferences sharedPref =
                context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);

        wireLeafletLicense(view);
        wireDoubleField(view, R.id.et_DMockLat, "dLat", sharedPref);
        wireDoubleField(view, R.id.et_DMockLon, "dLng", sharedPref);
        wireIntField(view, R.id.et_MockCount, "mockCount", 0, sharedPref, /*minClamp*/ 0, /*maxClamp*/ 100);
        wireIntField(view, R.id.et_MockFrequency, "mockFrequency", 10, sharedPref, /*minClamp*/ 1, /*maxClamp*/ 60);
        wireTimingSliders(view, sharedPref);
        wireCheckBox(view, R.id.cb_MockSpeed, "mockSpeed", true);
        wireMapProvider(view, sharedPref);
        wireRestoreAfterBoot(view);                                                      // FIX-005
        wireFeatureModules(view);                                                        // P6–P8
        wireOemHelper(view);                                                             // FIX-011
        wireOemCard(view);
        wireVersionFooter(view);
        wireResetDefaults(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshOemCard(requireView());
    }

    /** Populates the read-only version footer. The 7-tap debug-console gate that used to
     *  live here is gone — T10 rehomes it in the new AboutActivity. */
    private void wireVersionFooter(@NonNull View view) {
        final TextView v = view.findViewById(R.id.tv_AppVersion);
        if (v == null) return;
        try {
            final PackageInfo pi = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            v.setText(getString(R.string.More_VersionFooter,
                    pi.versionName, PackageInfoUtil.versionCode(pi)));
        } catch (Throwable ignored) {}
    }

    /** Binds the static parts of the OEM whitelist card. Status text refreshes per
     *  {@link #onResume()} so the card reflects changes after the user returns from the
     *  battery-optimisation settings screen. */
    private void wireOemCard(@NonNull View view) {
        final MaterialButton fix = view.findViewById(R.id.oem_card_fix_button);
        if (fix != null) fix.setOnClickListener(v -> {
            if (requireActivity() instanceof AppCompatActivity)
                OemBatteryOptHelper.showDialog((AppCompatActivity) requireActivity());
        });
    }

    private void refreshOemCard(@NonNull View view) {
        final TextView status = view.findViewById(R.id.oem_card_status);
        final TextView instr = view.findViewById(R.id.oem_card_instructions);
        final MaterialButton fix = view.findViewById(R.id.oem_card_fix_button);
        if (status == null || instr == null || fix == null) return;
        final boolean whitelisted = OemBatteryOptHelper.isWhitelisted(requireContext());
        status.setText(whitelisted ? R.string.Oem_Card_Status_OK : R.string.Oem_Card_Status_Need);
        instr.setText(OemBatteryOptHelper.getInstructions(requireContext()));
        fix.setEnabled(!whitelisted);
    }

    private void wireLeafletLicense(@NonNull View view) {
        final TextView tv = Inputs.requireView(view, R.id.tv_LeafletLicense, "tv_LeafletLicense");
        tv.setText(fromHtml(getString(R.string.ActivityMore_LeafletLicense)));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void wireDoubleField(@NonNull View view, int id, @NonNull String key,
                                 @NonNull SharedPreferences sp) {
        final EditText et = Inputs.requireView(view, id, "wireDoubleField:" + key);
        et.setText(DECIMAL_FORMAT.format(getDouble(sp, key, 0)));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                final SharedPreferences.Editor e = requireContext().getApplicationContext()
                        .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
                if (et.getText().toString().trim().isEmpty()) {
                    putDouble(e, key, 0);
                } else {
                    try {
                        final double parsed = Double.parseDouble(et.getText().toString());
                        if (Double.isFinite(parsed)) putDouble(e, key, parsed);
                    } catch (Throwable t) {
                        Log.e(TAG, "Could not parse " + key + "!", t);
                    }
                }
                e.apply();
            }
        });
    }

    private void wireIntField(@NonNull View view, int id, @NonNull String key, int dflt,
                              @NonNull SharedPreferences sp, int minClamp, int maxClamp) {
        final EditText et = Inputs.requireView(view, id, "wireIntField:" + key);
        et.setText(String.format(Locale.ROOT, "%d", Math.max(minClamp, Math.min(maxClamp, sp.getInt(key, dflt)))));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                final SharedPreferences.Editor e = requireContext().getApplicationContext()
                        .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
                int value = dflt;
                if (!et.getText().toString().trim().isEmpty()) {
                    try {
                        value = Integer.parseInt(et.getText().toString());
                    } catch (Throwable t) {
                        Log.e(TAG, "Could not parse " + key + "!", t);
                    }
                }
                if (value < minClamp) value = minClamp;
                if (value > maxClamp) value = maxClamp;
                e.putInt(key, value).apply();
            }
        });
    }

    private void wireTimingSliders(@NonNull View view, @NonNull SharedPreferences sp) {
        wireSlider(view, R.id.slider_MockFrequency, R.id.mock_frequency_value,
                R.id.et_MockFrequency, sp.getInt("mockFrequency", 10), 1, 60, false);
        wireSlider(view, R.id.slider_MockCount, R.id.mock_count_value,
                R.id.et_MockCount, sp.getInt("mockCount", 0), 0, 100, true);
    }

    private void wireSlider(@NonNull View view, int sliderId, int labelId, int editId,
                            int current, int min, int max, boolean infiniteZero) {
        final Slider slider = view.findViewById(sliderId);
        final TextView label = view.findViewById(labelId);
        final EditText edit = view.findViewById(editId);
        if (slider == null || label == null || edit == null) return;
        final int clamped = Math.max(min, Math.min(max, current));
        slider.setValue(clamped);
        label.setText(formatSliderValue(clamped, infiniteZero));
        // Per-slider guard prevents the slider→EditText setText from re-triggering the
        // slider listener for this specific pair; using boolean[] for lambda capture.
        final boolean[] syncing = {false};
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            try {
                final int rounded = Math.round(value);
                label.setText(formatSliderValue(rounded, infiniteZero));
                if (fromUser) edit.setText(String.format(Locale.ROOT, "%d", rounded));
            } finally {
                syncing[0] = false;
            }
        });
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                syncing[0] = true;
                try {
                    if (s.toString().trim().isEmpty()) return;
                    int value;
                    try {
                        value = Integer.parseInt(s.toString());
                    } catch (NumberFormatException e) {
                        return;
                    }
                    final int clampedValue = Math.max(min, Math.min(max, value));
                    slider.setValue(clampedValue);
                    label.setText(formatSliderValue(clampedValue, infiniteZero));
                } finally {
                    syncing[0] = false;
                }
            }
        });
    }

    @NonNull
    private String formatSliderValue(int value, boolean infiniteZero) {
        if (getContext() == null) return String.valueOf(value);
        if (infiniteZero && value == 0) return getString(R.string.QuickSettings_Count_Infinite);
        return infiniteZero
                ? getString(R.string.QuickSettings_Count_Finite, value)
                : getString(R.string.Settings_UpdateInterval_Value, value);
    }

    private void wireCheckBox(@NonNull View view, int id, @NonNull String key, boolean dflt) {
        final CompoundButton cb = Inputs.requireView(view, id, "wireCheckBox:" + key);
        final SharedPreferences sp = requireContext().getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        cb.setChecked(sp.getBoolean(key, dflt));
        cb.setOnCheckedChangeListener((b, checked) -> requireContext().getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit()
                .putBoolean(key, checked).apply());
    }

    private void wireMapProvider(@NonNull View view, @NonNull SharedPreferences sharedPref) {
        final EditText etMap = Inputs.requireView(view, R.id.et_MapProvider, "et_MapProvider");
        etMap.setText(sharedPref.getString("mapProvider",
                MapProviderUtil.getDefaultMapProvider(Locale.getDefault())));
        etMap.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                final SharedPreferences.Editor e = requireContext().getApplicationContext()
                        .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
                final String text = etMap.getText().toString();
                e.putString("mapProvider", text.trim().isEmpty()
                        ? MapProviderUtil.getDefaultMapProvider(Locale.getDefault())
                        : text);
                e.apply();
            }
        });
    }

    private void wireRestoreAfterBoot(@NonNull View view) {                              // FIX-005
        final CompoundButton cb = Inputs.requireView(view, R.id.cb_RestoreAfterBoot, "cb_RestoreAfterBoot");
        cb.setChecked(SharedPrefsUtil.isRestoreAfterBoot(requireContext().getApplicationContext()));
        cb.setOnCheckedChangeListener((b, checked) ->
                SharedPrefsUtil.setRestoreAfterBoot(requireContext().getApplicationContext(), checked));
    }

    /** P6–P8: one switch per {@link FeatureFlag} plus the Module 1 simulation prefs. Reuses
     *  {@link #wireCheckBox} so each toggle reads/writes the same SharedPreferences key the
     *  modules consult ({@code FeatureFlag.prefKey} / {@code simulateAltitude} / etc). */
    private void wireFeatureModules(@NonNull View view) {
        wireCheckBox(view, R.id.cb_feat_session_history,
                FeatureFlag.SESSION_HISTORY.prefKey, FeatureFlag.SESSION_HISTORY.defaultValue);
        wireCheckBox(view, R.id.cb_feat_route_lab,
                FeatureFlag.ROUTE_LAB.prefKey, FeatureFlag.ROUTE_LAB.defaultValue);
        wireCheckBox(view, R.id.cb_feat_geofence,
                FeatureFlag.GEOFENCE_LAB.prefKey, FeatureFlag.GEOFENCE_LAB.defaultValue);
        wireCheckBox(view, R.id.cb_feat_perm_drift,
                FeatureFlag.PERMISSION_DRIFT.prefKey, FeatureFlag.PERMISSION_DRIFT.defaultValue);
        wireCheckBox(view, R.id.cb_feat_exif,
                FeatureFlag.EXIF_CLEANER.prefKey, FeatureFlag.EXIF_CLEANER.defaultValue);
        wireCheckBox(view, R.id.cb_feat_privacy_wipe,
                FeatureFlag.PRIVACY_WIPE.prefKey, FeatureFlag.PRIVACY_WIPE.defaultValue);
        wireCheckBox(view, R.id.cb_feat_evidence,
                FeatureFlag.EVIDENCE_EXPORT.prefKey, FeatureFlag.EVIDENCE_EXPORT.defaultValue);
        wireCheckBox(view, R.id.cb_SimulateAltitude, "simulateAltitude", false);
        wireCheckBox(view, R.id.cb_SimulateAccuracy, "simulateAccuracy", false);
        wireStringField(view, R.id.et_SessionLabel, "sessionLabel");
    }

    private void wireStringField(@NonNull View view, int id, @NonNull String key) {
        final EditText et = view.findViewById(id);
        if (et == null) return;
        final SharedPreferences sp = requireContext().getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        et.setText(sp.getString(key, ""));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                requireContext().getApplicationContext()
                        .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit()
                        .putString(key, et.getText().toString()).apply();
            }
        });
    }

    private void wireResetDefaults(@NonNull View view) {
        final MaterialButton btn = view.findViewById(R.id.settings_reset_defaults);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            final Context ctx = requireContext().getApplicationContext();
            final SharedPreferences.Editor e =
                    ctx.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
            putDouble(e, "dLat", 0);
            putDouble(e, "dLng", 0);
            e.putInt("mockCount", 0);
            e.putInt("mockFrequency", 10);
            e.putBoolean("mockSpeed", true);
            e.putString("mapProvider",
                    MapProviderUtil.getDefaultMapProvider(Locale.getDefault()));
            e.apply();
            SharedPrefsUtil.setRestoreAfterBoot(ctx, false);
            dismiss();
        });
    }

    private void wireOemHelper(@NonNull View view) {                                     // FIX-011
        final MaterialButton btn = Inputs.requireView(view, R.id.btn_OemHelper, "btn_OemHelper");
        btn.setOnClickListener(v -> {
            if (requireActivity() instanceof AppCompatActivity)
                OemBatteryOptHelper.showDialog((AppCompatActivity) requireActivity());
        });
    }

    private static Spanned fromHtml(@NonNull String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        return fromLegacyHtml(html);
    }

    @SuppressWarnings("deprecation")
    private static Spanned fromLegacyHtml(@NonNull String html) {
        return Html.fromHtml(html);
    }
}

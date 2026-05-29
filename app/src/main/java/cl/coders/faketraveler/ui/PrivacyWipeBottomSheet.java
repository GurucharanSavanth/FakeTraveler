package cl.coders.faketraveler.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import cl.coders.faketraveler.MainActivity;
import cl.coders.faketraveler.PrivacyWipeEngine;
import cl.coders.faketraveler.PrivacyWipeWorker;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.ModuleRepository;

/**
 * Module 7: category checkboxes + a 3-second-countdown "Wipe now", an immediate "Emergency wipe"
 * (no countdown), a scheduled-wipe toggle, and a history list. Emergency wipe selects every category
 * except app data; "Wipe now" honours the checkboxes. All deletes run on the repo IO thread.
 */
public class PrivacyWipeBottomSheet extends BottomSheetDialogFragment {

    private static final int COUNTDOWN_SECONDS = 3;

    @Nullable private WipeLogAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_privacy_wipe, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Context appCtx = requireContext().getApplicationContext();

        final RecyclerView list = view.findViewById(R.id.wipe_log_list);
        adapter = new WipeLogAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        final MaterialSwitch schedule = view.findViewById(R.id.wipe_schedule_switch);
        schedule.setChecked(appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                .getBoolean(PrivacyWipeWorker.PREF_SCHEDULED, false));
        schedule.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                    .edit().putBoolean(PrivacyWipeWorker.PREF_SCHEDULED, checked).apply();
            if (checked) PrivacyWipeWorker.scheduleNext(appCtx);
            else PrivacyWipeWorker.cancel(appCtx);
        });

        view.findViewById(R.id.wipe_now_btn).setOnClickListener(v -> confirmWipe(view));
        view.findViewById(R.id.wipe_emergency_btn).setOnClickListener(v -> emergencyWipe());

        AppDatabase.get(requireContext()).privacyWipeLogDao().getAllWipes().observe(
                getViewLifecycleOwner(),
                logs -> { if (adapter != null) adapter.submit(logs); });
    }

    @NonNull
    private PrivacyWipeEngine.Options optionsFrom(@NonNull View root) {
        final PrivacyWipeEngine.Options o = new PrivacyWipeEngine.Options();
        o.sessionHistory = checked(root, R.id.wipe_cb_sessions);
        o.geofenceEvents = checked(root, R.id.wipe_cb_geofence);
        o.permissions = checked(root, R.id.wipe_cb_permissions);
        o.exifBackups = checked(root, R.id.wipe_cb_exif);
        o.appData = checked(root, R.id.wipe_cb_appdata);
        return o;
    }

    private static boolean checked(@NonNull View root, int id) {
        final MaterialCheckBox cb = root.findViewById(id);
        return cb != null && cb.isChecked();
    }

    private void confirmWipe(@NonNull View root) {
        if (!isAdded()) return;
        final PrivacyWipeEngine.Options o = optionsFrom(root);
        if (!o.any()) {
            Toast.makeText(requireContext(), R.string.PrivacyWipe_NothingSelected, Toast.LENGTH_SHORT).show();
            return;
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.PrivacyWipe_Confirm_Title)
                .setMessage(R.string.PrivacyWipe_Confirm_Message)
                .setPositiveButton(R.string.PrivacyWipe_Now, (d, w) -> runWipe(o))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(di -> startCountdown(dialog));
        dialog.show();
    }

    /** Disable the confirm button and count it down from {@link #COUNTDOWN_SECONDS} to prevent taps. */
    private void startCountdown(@NonNull AlertDialog dialog) {
        final Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ok == null) return;
        final Handler handler = new Handler(Looper.getMainLooper());
        final int[] remaining = {COUNTDOWN_SECONDS};
        ok.setEnabled(false);
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!isAdded() || !dialog.isShowing()) return;
                if (remaining[0] <= 0) {
                    ok.setEnabled(true);
                    ok.setText(R.string.PrivacyWipe_Now);
                } else {
                    ok.setText(getString(R.string.PrivacyWipe_Countdown, remaining[0]));
                    remaining[0]--;
                    handler.postDelayed(this, 1000L);
                }
            }
        };
        handler.post(tick);
    }

    private void emergencyWipe() {
        final PrivacyWipeEngine.Options o = new PrivacyWipeEngine.Options();
        o.sessionHistory = true;
        o.geofenceEvents = true;
        o.permissions = true;
        o.exifBackups = true;
        runWipe(o);
        if (isAdded()) Toast.makeText(requireContext(), R.string.PrivacyWipe_Done, Toast.LENGTH_SHORT).show();
    }

    private void runWipe(@NonNull PrivacyWipeEngine.Options o) {
        final Context appCtx = requireContext().getApplicationContext();
        ModuleRepository.get(appCtx).io(() -> new PrivacyWipeEngine(appCtx).wipe(o));
    }
}

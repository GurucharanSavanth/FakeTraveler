package cl.coders.faketraveler.cooldown;

import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Duration;
import java.util.Locale;

import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.R;

/**
 * Blocks the Apply action with a countdown derived from {@link CooldownCalculator}.
 * The user can either wait out the timer or check the risk-acknowledgement box to enable
 * the Override button. Every override is logged so the debug console preserves a forensic
 * trail (V39).
 */
public class CooldownOverlayDialog extends DialogFragment {

    public interface OnOverride { void onOverride(double distanceKm); }

    @Nullable private OnOverride onOverride;
    private long remainingMs;
    private double distanceKm;
    @Nullable private CountDownTimer timer;

    @NonNull
    public static CooldownOverlayDialog newInstance(@NonNull Duration cooldown, double distanceKm) {
        CooldownOverlayDialog d = new CooldownOverlayDialog();
        Bundle b = new Bundle();
        b.putLong("ms", cooldown.toMillis());
        b.putDouble("km", distanceKm);
        d.setArguments(b);
        return d;
    }

    public void setOnOverride(@Nullable OnOverride cb) { onOverride = cb; }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle s) {
        remainingMs = requireArguments().getLong("ms");
        distanceKm = requireArguments().getDouble("km");

        // Use the fragment-scoped inflater so the dialog content inherits the host theme.
        View content = getLayoutInflater().inflate(R.layout.dialog_cooldown, null, false);
        TextView countdown = content.findViewById(R.id.cooldown_text);
        CheckBox ack = content.findViewById(R.id.cooldown_ack);
        MaterialButton override = content.findViewById(R.id.cooldown_override);
        override.setEnabled(false);
        ack.setOnCheckedChangeListener((b, isChecked) -> override.setEnabled(isChecked));
        override.setOnClickListener(b -> {
            MockLogger.log("cooldown_override",
                    String.format(Locale.US, "%.2fkm", distanceKm));
            if (onOverride != null) onOverride.onOverride(distanceKm);
            dismiss();
        });

        timer = new CountDownTimer(remainingMs, 1_000L) {
            @Override public void onTick(long ms) {
                long secs = ms / 1_000L;
                countdown.setText(String.format(Locale.US, "%d:%02d", secs / 60, secs % 60));
            }
            @Override public void onFinish() {
                countdown.setText(R.string.Cooldown_Elapsed);
                if (onOverride != null) onOverride.onOverride(distanceKm);
                dismiss();
            }
        }.start();

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.Cooldown_Title)
                .setView(content)
                .setCancelable(false)
                .create();
    }

    @Override
    public void onDestroyView() {
        if (timer != null) timer.cancel();
        super.onDestroyView();
    }
}

package cl.coders.faketraveler;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;

/**
 * Wires the lat/lng {@link EditText} widgets to a callback that fires when the user types
 * a valid coordinate. Programmatic updates (from the map or shared prefs) bypass the
 * callback via {@link #setProgrammatic(double, double)}. FIX-015.
 */
public final class LocationInputHandler {

    /** Callback for validated user-typed coordinate changes. Java-8 safe on minSdk 21. */
    public interface CoordConsumer {
        void accept(double lat, double lng);
    }

    @NonNull
    private static final String TAG = LocationInputHandler.class.getSimpleName();

    @NonNull
    private final EditText etLat;
    @NonNull
    private final EditText etLng;
    @NonNull
    private final CoordConsumer onUserEdit;

    private boolean suppress = false;

    /**
     * @param etLat       latitude EditText
     * @param etLng       longitude EditText
     * @param onUserEdit  callback (lat, lng) invoked after a valid user-typed change
     */
    public LocationInputHandler(@NonNull EditText etLat,
                                @NonNull EditText etLng,
                                @NonNull CoordConsumer onUserEdit) {
        this.etLat = etLat;
        this.etLng = etLng;
        this.onUserEdit = onUserEdit;
        this.etLat.addTextChangedListener(new LatWatcher());
        this.etLng.addTextChangedListener(new LngWatcher());
    }

    /** Update both EditTexts without firing the user-edit callback. */
    public void setProgrammatic(double lat, double lng) {
        suppress = true;
        try {
            etLat.setText(MainActivity.DECIMAL_FORMAT.format(lat));
            etLng.setText(MainActivity.DECIMAL_FORMAT.format(lng));
        } finally {
            suppress = false;
        }
    }

    public static double clampLat(double v) {
        if (Double.isNaN(v)) return 0d;
        return Math.max(-90d, Math.min(90d, v));
    }

    public static double clampLng(double v) {
        if (Double.isNaN(v)) return 0d;
        return Math.max(-180d, Math.min(180d, v));
    }

    private void emitIfBothValid() {
        try {
            final String latStr = etLat.getText().toString();
            final String lngStr = etLng.getText().toString();
            if (latStr.isEmpty() || "-".equals(latStr)) return;
            if (lngStr.isEmpty() || "-".equals(lngStr)) return;
            final double lat = clampLat(Double.parseDouble(latStr));
            final double lng = clampLng(Double.parseDouble(lngStr));
            onUserEdit.accept(lat, lng);
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Invalid coordinate input", nfe);
        }
    }

    private class LatWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(Editable s) {
            if (suppress) return;
            emitIfBothValid();
        }
    }

    private class LngWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(Editable s) {
            if (suppress) return;
            emitIfBothValid();
        }
    }
}

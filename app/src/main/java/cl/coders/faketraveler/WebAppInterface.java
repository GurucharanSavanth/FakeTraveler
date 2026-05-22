package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;

import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;

import cl.coders.faketraveler.util.Inputs;


public class WebAppInterface {

    private static final String TAG = WebAppInterface.class.getSimpleName();
    private static final double ZOOM_MIN = 0d;
    private static final double ZOOM_MAX = 22d;

    @NonNull
    private final MainActivity mainActivity;

    public WebAppInterface(@NonNull MainActivity mA) {
        mainActivity = mA;
    }

    /**
     * Set position in GUI. Called by JS on long-press in map. Format: "(lat, lng)".
     * Input validated before substring; malformed input is logged and ignored.
     * Poison-pill: parses through {@link Inputs#parseDoubleSafe} and rejects NaN/Infinity
     * + out-of-range lat/lng so WebView cannot inject crash inputs through the bridge.
     */
    @JavascriptInterface
    public void setPosition(final String str) {
        mainActivity.runOnUiThread(() -> {
            try {
                if (str == null) return;
                final int lp = str.indexOf('(');
                final int comma = str.indexOf(',');
                final int rp = str.indexOf(')');
                if (lp < 0 || comma <= lp || rp <= comma) {
                    Log.w(TAG, "setPosition: malformed payload, expected \"(lat, lng)\"");
                    return;
                }
                final String latStr = str.substring(lp + 1, comma).trim();
                final String lngStr = str.substring(comma + 1, rp).trim();
                final double lat = Inputs.parseDoubleSafe(latStr, Double.NaN);
                final double lng = Inputs.parseDoubleSafe(lngStr, Double.NaN);
                if (!Inputs.validLat(lat) || !Inputs.validLng(lng)) {
                    Log.w(TAG, "setPosition: lat/lng out of range or non-finite");
                    return;
                }
                mainActivity.setLatLng(lat, lng, CHANGE_FROM_MAP);
            } catch (Throwable t) {
                Log.e(TAG, "Could not set new position from map!", t);
            }
        });
    }

    @JavascriptInterface
    public void setZoom(final String str) {
        mainActivity.runOnUiThread(() -> {
            try {
                if (str == null) return;
                final double z = Inputs.parseDoubleSafe(str, Double.NaN);
                if (!Inputs.isFinite(z) || z < ZOOM_MIN || z > ZOOM_MAX) {
                    Log.w(TAG, "setZoom: rejected out-of-range value");
                    return;
                }
                mainActivity.setZoom(z);
            } catch (Throwable t) {
                Log.e(TAG, "Could not save zoom!", t);
            }
        });
    }

}

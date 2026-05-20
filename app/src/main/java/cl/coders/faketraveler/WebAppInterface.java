package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;

import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;


public class WebAppInterface {

    @NonNull
    private final MainActivity mainActivity;

    public WebAppInterface(@NonNull MainActivity mA) {
        mainActivity = mA;
    }

    /**
     * Set position in GUI. Called by JS on long-press in map. Format: "(lat, lng)".
     * Input validated before substring; malformed input is logged and ignored.
     * FIX-020 (DEFECT-NEW-001): drill-sergeant Sin #8 — bounds check before substring.
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
                    Log.w(WebAppInterface.class.getSimpleName(),
                            "setPosition: malformed payload, expected \"(lat, lng)\"");
                    return;
                }
                final String lat = str.substring(lp + 1, comma);
                final String lng = str.substring(comma + 2, rp);
                mainActivity.setLatLng(Double.parseDouble(lat), Double.parseDouble(lng), CHANGE_FROM_MAP);
            } catch (Throwable t) {
                Log.e(WebAppInterface.class.getSimpleName(), "Could not set new position from map!", t);
            }
        });
    }

    @JavascriptInterface
    public void setZoom(final String str) {
        mainActivity.runOnUiThread(() -> {
            try {
                if (str == null) return;
                mainActivity.setZoom(Double.parseDouble(str));
            } catch (Throwable t) {
                Log.e(WebAppInterface.class.getSimpleName(), "Could not save zoom!", t);
            }
        });
    }

}

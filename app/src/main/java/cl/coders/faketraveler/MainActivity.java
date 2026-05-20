package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_EDITTEXT;
import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;
import static cl.coders.faketraveler.MainActivity.SourceChange.LOAD;
import static cl.coders.faketraveler.SharedPrefsUtil.getDouble;
import static cl.coders.faketraveler.SharedPrefsUtil.putDouble;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ServiceConnector.Listener {

    @NonNull private static final String TAG = MainActivity.class.getSimpleName();
    @NonNull public static final String sharedPrefKey = "cl.coders.faketraveler.sharedprefs";
    @NonNull public static final DecimalFormat DECIMAL_FORMAT =
            new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private MaterialButton buttonApplyStop;
    private WebView webView;
    private EditText editTextLat;
    private EditText editTextLng;
    private Context context;
    private int currentVersion;
    @Nullable private ServiceConnector serviceConnector;
    @Nullable private LocationInputHandler inputHandler;

    // Persisted config
    private int version;
    private double lat, lng, zoom, dLat, dLng;
    private int mockCount, mockFrequency;
    private boolean mockSpeed;
    private long endTime;
    private String mapProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        context = getApplicationContext();
        webView = findViewById(R.id.webView0);
        editTextLat = findViewById(R.id.editTextLat);
        editTextLng = findViewById(R.id.editTextLng);
        buttonApplyStop = findViewById(R.id.button_applyStop);
        final MaterialButton buttonSettings = findViewById(R.id.button_settings);

        final WebAppInterface bridge = new WebAppInterface(this);
        WebViewSetup.configure(webView, bridge);                                         // FIX-007
        inputHandler = new LocationInputHandler(editTextLat, editTextLng,
                (lat, lng) -> setLatLng(lat, lng, CHANGE_FROM_EDITTEXT));                // FIX-015 (CoordConsumer)
        serviceConnector = new ServiceConnector(this, this);                             // FIX-015

        buttonApplyStop.setOnClickListener(view -> applyLocation());
        buttonSettings.setOnClickListener(view ->
                startActivity(new Intent(getBaseContext(), MoreActivity.class)));

        detectAppVersion();
        loadSharedPrefs();
        applyIntentOrDefault(getIntent());

        if (endTime > System.currentTimeMillis()) {
            // FIX-028 (Phase 3.5): after process death, RESUME-start the foreground service
            // so onStartCommand triggers resumeFromPrefsIfActive, then bind for UI updates.
            serviceConnector.resumeAndBind();
            changeButtonToStop();
        } else {
            endTime = 0;
            saveSettings();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // FIX-030 (Phase 3.8): preserve WebView state across configuration changes (rotate, etc).
        if (webView != null) {
            try {
                final Bundle webState = new Bundle();
                webView.saveState(webState);
                outState.putBundle("webView_state", webState);
            } catch (Throwable t) {
                Log.w(TAG, "WebView saveState failed", t);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (webView != null) {
            final Bundle webState = savedInstanceState.getBundle("webView_state");
            if (webState != null) {
                try { webView.restoreState(webState); }
                catch (Throwable t) { Log.w(TAG, "WebView restoreState failed", t); }
            }
        }
    }

    private void detectAppVersion() {
        try {
            final PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersion = PackageInfoUtil.versionCode(pInfo);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not read version info!", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        context = getApplicationContext();
        loadSharedPrefs();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        loadSharedPrefs();
        applyIntentOrDefault(intent);
    }

    @Override
    public void onDestroy() {                                                            // FIX-015
        if (serviceConnector != null) serviceConnector.unbind();
        serviceConnector = null;
        inputHandler = null;
        super.onDestroy();
    }

    private void loadSharedPrefs() {
        SharedPrefsUtil.migrateOldPreferences(context);
        SharedPrefsUtil.migrateToV2(context);                                            // FIX-014
        final SharedPreferences sp = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        version = sp.getInt("version", 0);
        lat = getDouble(sp, "lat", 12);
        lng = getDouble(sp, "lng", 15);
        zoom = getDouble(sp, "zoom", 12);
        mockCount = sp.getInt("mockCount", 0);
        mockFrequency = sp.getInt("mockFrequency", 10);
        if (mockFrequency <= 0) mockFrequency = 1;
        dLat = getDouble(sp, "dLat", 0);
        dLng = getDouble(sp, "dLng", 0);
        mockSpeed = sp.getBoolean("mockSpeed", true);
        endTime = sp.getLong("endTime", 0);
        mapProvider = sp.getString("mapProvider", MapProviderUtil.getDefaultMapProvider(Locale.getDefault()));
        if (version != currentVersion) {
            version = currentVersion;
            saveSettings();
        }
    }

    private void saveSettings() {
        final Editor e = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
        e.putInt("version", version);
        putDouble(e, "lat", lat);
        putDouble(e, "lng", lng);
        putDouble(e, "zoom", zoom);
        e.putInt("mockCount", mockCount);
        e.putInt("mockFrequency", mockFrequency);
        putDouble(e, "dLat", dLat);
        putDouble(e, "dLng", dLng);
        e.putBoolean("mockSpeed", mockSpeed);
        e.putLong("endTime", endTime);
        e.putString("mapProvider", mapProvider);
        e.apply();
    }

    private void applyIntentOrDefault(@NonNull Intent intent) {
        final String intentData = intent.getDataString();
        if (intentData != null) {
            try {
                final GeoUri uri = GeoUri.parse(intentData);
                if (uri == null) {
                    showError(R.string.MainActivity_NoLatLong);                          // FIX-029
                } else {
                    lat = LocationInputHandler.clampLat(uri.lat());
                    lng = LocationInputHandler.clampLng(uri.lng());
                    final Double zoomTmp = uri.zoom();
                    if (zoomTmp != null) zoom = zoomTmp;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Could not read geo intent!", t);
                showError(R.string.MainActivity_NoLatLong);                              // FIX-029
            }
        }
        setLatLng(lat, lng, LOAD);
        webView.loadUrl(Uri.parse("file:///android_asset/map.html").buildUpon()
                .appendQueryParameter("lat", "" + lat)
                .appendQueryParameter("lng", "" + lng)
                .appendQueryParameter("zoom", "" + zoom)
                .appendQueryParameter("provider", mapProvider)
                .build().toString());
    }

    /** Apply mock with permission pre-flight. FIX-009. */
    protected void applyLocation() {
        if (isBlank(editTextLat) || isBlank(editTextLng)) {
            showError(R.string.MainActivity_NoLatLong);                                  // FIX-025
            return;
        }
        if (!PermissionChecker.isMockLocationEnabled(context)) {                         // FIX-009
            PermissionChecker.showDevSettingsDialog(this);                               // FIX-008
            return;
        }
        if (serviceConnector == null) return;
        lat = LocationInputHandler.clampLat(Double.parseDouble(editTextLat.getText().toString()));
        lng = LocationInputHandler.clampLng(Double.parseDouble(editTextLng.getText().toString()));
        final float[] speed = {0f};
        if (mockSpeed) {
            // FIX-023 (Phase 3.3): m/s = distance_per_tick(m) / interval(s).
            // mockFrequency is already in seconds; prior code divided by *1000L = m/ms.
            Location.distanceBetween(lat, lng, lat + dLat / 1_000_000d, lng + dLng / 1_000_000d, speed);
            speed[0] /= Math.max(1, mockFrequency);
        }
        serviceConnector.startAndBindForApply(new ServiceConnector.MockArgs(
                lat, lng, dLat / 1_000_000d, dLng / 1_000_000d,
                mockFrequency * 1000L, mockCount, speed[0]));
        // FIX-024 (Phase 3.2): mockCount==0 means infinite — sentinel endTime so UI
        // recognises "still running" instead of computing a past timestamp.
        endTime = mockCount == 0
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + (mockCount - 1L) * mockFrequency * 1000L;
        saveSettings();
    }

    void showSnackbar(@NonNull String s) {
        Snackbar.make(findViewById(R.id.main_layout), s, Snackbar.LENGTH_SHORT).show();
    }
    void showSnackbar(@StringRes int strRes) {
        Snackbar.make(findViewById(R.id.main_layout), strRes, Snackbar.LENGTH_SHORT).show();
    }
    /** FIX-025 (Phase 2.3): errors get LENGTH_LONG so users have time to read. */
    void showError(@StringRes int strRes) {
        Snackbar.make(findViewById(R.id.main_layout), strRes, Snackbar.LENGTH_LONG).show();
    }
    private static boolean isBlank(@NonNull EditText et) {
        return et.getText().toString().isBlank();
    }
    protected void setMapMarker(double lat, double lng) {
        if (webView == null || webView.getUrl() == null) return;
        webView.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");");
    }
    void changeButtonToApply() {
        buttonApplyStop.setText(context.getResources().getString(R.string.ActivityMain_Apply));
        buttonApplyStop.setOnClickListener(view -> applyLocation());
    }
    void changeButtonToStop() {
        buttonApplyStop.setText(context.getResources().getString(R.string.ActivityMain_Stop));
        buttonApplyStop.setOnClickListener(view -> {
            if (serviceConnector != null) serviceConnector.requestStop();
        });
    }
    public void setZoom(double zoom) {
        this.zoom = zoom;
        saveSettings();
    }

    /** Update lat/lng + UI; srcChange controls which surface gets the update. */
    void setLatLng(double mLat, double mLng, @NonNull SourceChange srcChange) {
        lat = LocationInputHandler.clampLat(mLat);
        lng = LocationInputHandler.clampLng(mLng);
        if (srcChange == CHANGE_FROM_EDITTEXT || srcChange == LOAD) setMapMarker(lat, lng);
        if ((srcChange == CHANGE_FROM_MAP || srcChange == LOAD) && inputHandler != null) {
            inputHandler.setProgrammatic(lat, lng);
        }
        saveSettings();
    }

    @Override
    public void onMockedStateChange(@NonNull MockState state) {
        switch (state) {
            case NOT_MOCKED -> { showSnackbar(R.string.MainActivity_MockStopped); changeButtonToApply(); endTime = 0; saveSettings(); }
            case SERVICE_BOUND -> { if (endTime > System.currentTimeMillis()) changeButtonToStop(); }
            case MOCKED -> { changeButtonToStop(); showSnackbar(R.string.MainActivity_MockApplied); }
            case MOCK_ERROR -> PermissionChecker.showDevSettingsDialog(this);            // FIX-008
        }
    }

    @Override
    public void onMockedLocationChange(@NonNull Location location) {
        setMapMarker(location.getLatitude(), location.getLongitude());
    }

    public enum SourceChange { NONE, LOAD, CHANGE_FROM_EDITTEXT, CHANGE_FROM_MAP }
}

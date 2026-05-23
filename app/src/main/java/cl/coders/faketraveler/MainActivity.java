package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_EDITTEXT;
import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;
import static cl.coders.faketraveler.MainActivity.SourceChange.LOAD;
import static cl.coders.faketraveler.SharedPrefsUtil.getDouble;
import static cl.coders.faketraveler.SharedPrefsUtil.putDouble;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.text.InputType;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import cl.coders.faketraveler.util.Inputs;

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

    /** API 34+ FGS type=location requires ACCESS_COARSE_LOCATION at runtime, otherwise
     *  startForeground throws SecurityException and the mock service crashes. */
    @NonNull
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    showError(R.string.MainActivity_LocationPermissionNeeded);
                }
            });

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
        // EdgeToEdge.enable must run before setContentView; the platform uses the theme
        // resolved at that point to decide light/dark system bars.
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        // Predictive back: warn if the user is about to walk away from an active mock.
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (endTime > System.currentTimeMillis()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.MainActivity_StopAndExitTitle)
                            .setMessage(R.string.MainActivity_StopAndExitMsg)
                            .setPositiveButton(R.string.Common_Yes, (d, w) -> {
                                if (serviceConnector != null) serviceConnector.requestStop();
                                finish();
                            })
                            .setNegativeButton(R.string.Common_No, null)
                            .show();
                } else {
                    // Disable this callback and re-dispatch so the system default (finish())
                    // runs instead of looping back into this handler.
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        context = getApplicationContext();
        webView = Inputs.requireView(this, R.id.webView0, "webView0");
        editTextLat = Inputs.requireView(this, R.id.editTextLat, "editTextLat");
        editTextLng = Inputs.requireView(this, R.id.editTextLng, "editTextLng");
        buttonApplyStop = Inputs.requireView(this, R.id.button_applyStop, "button_applyStop");
        final MaterialButton buttonSettings = Inputs.requireView(this, R.id.button_settings, "button_settings");

        final WebAppInterface bridge = new WebAppInterface(this);
        WebViewSetup.configure(webView, bridge);                                         // FIX-007
        inputHandler = new LocationInputHandler(editTextLat, editTextLng,
                (lat, lng) -> setLatLng(lat, lng, CHANGE_FROM_EDITTEXT));                // FIX-015 (CoordConsumer)
        serviceConnector = new ServiceConnector(this, this);                             // FIX-015

        buttonApplyStop.setOnClickListener(view -> applyLocation());
        buttonSettings.setOnClickListener(view -> showSettingsSheet());

        wireBookmarkButtons();
        wireDetectionButton();

        detectAppVersion();
        loadSharedPrefs();
        applyIntentOrDefault(getIntent());
        maybeRequestLocationPermission();

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

    private void maybeRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            } catch (Throwable t) {
                Log.w(TAG, "Could not request location permission", t);
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
        final double parsedLat = Inputs.parseDoubleSafe(editTextLat.getText().toString(), Double.NaN);
        final double parsedLng = Inputs.parseDoubleSafe(editTextLng.getText().toString(), Double.NaN);
        if (!Inputs.isFinite(parsedLat) || !Inputs.isFinite(parsedLng)) {
            showError(R.string.MainActivity_NoLatLong);
            return;
        }
        final double newLat = Inputs.clampLat(parsedLat);
        final double newLng = Inputs.clampLng(parsedLng);

        // Anti-detection cooldown: large jumps trigger rate-limiters in target apps. If a
        // wait is required, gate Apply behind CooldownOverlayDialog and resume from its
        // callback. See CooldownCalculator for the distance->wait table (V39).
        final double[] last = readLastMockedLatLng();
        if (last != null) {
            final java.time.Duration cd =
                    cl.coders.faketraveler.cooldown.CooldownCalculator.compute(
                            last[0], last[1], newLat, newLng);
            if (!cd.isZero()) {
                final double km = cl.coders.faketraveler.util.GeoUtils
                        .haversineKm(last[0], last[1], newLat, newLng);
                cl.coders.faketraveler.cooldown.CooldownOverlayDialog dlg =
                        cl.coders.faketraveler.cooldown.CooldownOverlayDialog.newInstance(cd, km);
                dlg.setOnOverride(dKm -> proceedWithApply(newLat, newLng));
                dlg.show(getSupportFragmentManager(), "cooldown");
                return;
            }
        }
        proceedWithApply(newLat, newLng);
    }

    /** Apply path after the cooldown gate. Split out so the dialog can re-enter it via
     *  its override callback. */
    private void proceedWithApply(double newLat, double newLng) {
        lat = newLat;
        lng = newLng;
        final float[] speed = {0f};
        if (mockSpeed) {
            // FIX-023 (Phase 3.3): m/s = distance_per_tick(m) / interval(s).
            Location.distanceBetween(lat, lng,
                    lat + dLat / 1_000_000d, lng + dLng / 1_000_000d, speed);
            speed[0] /= Math.max(1, mockFrequency);
        }
        if (serviceConnector == null) return;
        serviceConnector.startAndBindForApply(new ServiceConnector.MockArgs(
                lat, lng, dLat / 1_000_000d, dLng / 1_000_000d,
                mockFrequency * 1000L, mockCount, speed[0]));
        // FIX-024 (Phase 3.2): mockCount==0 means infinite — sentinel endTime so UI
        // recognises "still running" instead of computing a past timestamp.
        // Saturate arithmetic so pathological mockCount/mockFrequency values (corrupt
        // prefs, future schema bumps) can't overflow into a past timestamp.
        if (mockCount == 0) {
            endTime = Long.MAX_VALUE;
        } else {
            final long ticks = Math.max(0L, mockCount - 1L);
            final long freqMs = Math.max(0L, (long) mockFrequency) * 1000L;
            final long span = Inputs.saturatingMul(ticks, freqMs);
            endTime = Inputs.saturatingAdd(System.currentTimeMillis(), span);
        }
        saveSettings();
        MockLogger.log("mock_start", "lat=" + lat + " lng=" + lng + " freq=" + mockFrequency + "s");
        HealthCheckWorker.scheduleNext(this);
    }

    /** Parses {@code lastMockedLocation} JSON written by {@link SharedPrefsUtil}.
     *  Returns {@code null} when absent or unparseable — callers treat that as "no prior
     *  mock", which skips the cooldown gate. */
    @Nullable
    private double[] readLastMockedLatLng() {
        try {
            final String json = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE)
                    .getString(SharedPrefsUtil.KEY_LAST_MOCKED_LOCATION, "");
            if (json == null || json.isEmpty()) return null;
            org.json.JSONObject o = new org.json.JSONObject(json);
            final double lat = o.optDouble("lat", Double.NaN);
            final double lng = o.optDouble("lng", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lng)) return null;
            return new double[]{lat, lng};
        } catch (Throwable t) {
            return null;
        }
    }

    void showSnackbar(@NonNull String s) {
        Snackbar.make(Inputs.requireView(this, R.id.main_layout, "main_layout"),
                s, Snackbar.LENGTH_SHORT).show();
    }
    void showSnackbar(@StringRes int strRes) {
        Snackbar.make(Inputs.requireView(this, R.id.main_layout, "main_layout"),
                strRes, Snackbar.LENGTH_SHORT).show();
    }
    /** FIX-025 (Phase 2.3): errors get LENGTH_LONG so users have time to read. */
    void showError(@StringRes int strRes) {
        Snackbar.make(Inputs.requireView(this, R.id.main_layout, "main_layout"),
                strRes, Snackbar.LENGTH_LONG).show();
    }
    private static boolean isBlank(@NonNull EditText et) {
        return et.getText().toString().isBlank();
    }
    protected void setMapMarker(double lat, double lng) {
        if (webView == null || webView.getUrl() == null) return;
        final double safeLat = Inputs.clampLat(lat);
        final double safeLng = Inputs.clampLng(lng);
        webView.loadUrl("javascript:setOnMap("
                + Inputs.jsNumber(safeLat) + "," + Inputs.jsNumber(safeLng) + ");");
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
            case NOT_MOCKED -> {
                showSnackbar(R.string.MainActivity_MockStopped);
                changeButtonToApply();
                endTime = 0;
                saveSettings();
                MockLogger.log("mock_stop", "user or service");
                // Tear down the heartbeat + any pending auto-recovery so the worker chain
                // does not resurrect the mock after an explicit stop.
                HealthCheckWorker.cancel(this);
            }
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

    private void wireBookmarkButtons() {
        Inputs.<android.view.View>requireView(this, R.id.bookmark_save_btn, "bookmark_save_btn")
                .setOnClickListener(v -> showSaveBookmarkDialog());
        Inputs.<android.view.View>requireView(this, R.id.bookmark_list_btn, "bookmark_list_btn")
                .setOnClickListener(v -> showBookmarksSheet());
    }

    private void showSaveBookmarkDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.Bookmark_Dialog_Name_Hint);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.Bookmark_Dialog_Title_Add)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    final cl.coders.faketraveler.db.BookmarkEntity e =
                            new cl.coders.faketraveler.db.BookmarkEntity();
                    e.name = name;
                    e.lat = lat;
                    e.lng = lng;
                    e.zoom = (int) zoom;
                    e.createdAt = System.currentTimeMillis();
                    final Context appCtx = getApplicationContext();
                    final Thread io = new Thread(() ->
                            cl.coders.faketraveler.db.AppDatabase.get(appCtx).bookmarkDao().insert(e),
                            "BookmarksIO");
                    io.setDaemon(true);
                    io.start();
                    MockLogger.log("bookmark_save", name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showBookmarksSheet() {
        cl.coders.faketraveler.ui.BookmarksBottomSheet sheet =
                new cl.coders.faketraveler.ui.BookmarksBottomSheet();
        sheet.setCallback(fav -> {
            lat = fav.lat;
            lng = fav.lng;
            zoom = fav.zoom;
            setLatLng(lat, lng, SourceChange.LOAD);
            applyLocation();
            MockLogger.log("bookmark_apply", fav.name);
        });
        sheet.show(getSupportFragmentManager(), "bookmarks");
    }

    private void wireDetectionButton() {
        Inputs.<android.view.View>requireView(this, R.id.detection_btn, "detection_btn")
                .setOnClickListener(v -> showDetectionSheet());
    }

    private void showDetectionSheet() {
        new cl.coders.faketraveler.ui.DetectionTestBottomSheet()
                .show(getSupportFragmentManager(), "detection");
    }

    private void showSettingsSheet() {
        new cl.coders.faketraveler.ui.SettingsBottomSheet()
                .show(getSupportFragmentManager(), "settings");
    }
}

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
import android.content.res.ColorStateList;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

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
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import cl.coders.faketraveler.util.Inputs;

public class MainActivity extends AppCompatActivity implements ServiceConnector.Listener,
        cl.coders.faketraveler.ui.BookmarksBottomSheet.Host {

    @NonNull private static final String TAG = MainActivity.class.getSimpleName();
    @NonNull public static final String sharedPrefKey = "cl.coders.faketraveler.sharedprefs";
    @NonNull public static final DecimalFormat DECIMAL_FORMAT =
            new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private MaterialButton buttonApplyStop;
    @Nullable private Chip statusChip;
    @Nullable private TextView locationMetadata;
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

    /** Strong reference prevents GC; registered in onStart, unregistered in onStop. */
    @NonNull
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> {
                if (key == null) return;
                switch (key) {
                    case "mockFrequency":
                    case "mockCount":
                    case "dLat":
                    case "dLng":
                    case "mockSpeed":
                    case "mapProvider":
                    case "restoreAfterBoot":
                        loadSharedPrefs();
                        break;
                }
            };

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
        statusChip = findViewById(R.id.status_chip);
        locationMetadata = findViewById(R.id.location_metadata);

        final WebAppInterface bridge = new WebAppInterface(this);
        WebViewSetup.configure(webView, bridge);                                         // FIX-007
        inputHandler = new LocationInputHandler(editTextLat, editTextLng,
                (lat, lng) -> setLatLng(lat, lng, CHANGE_FROM_EDITTEXT));                // FIX-015 (CoordConsumer)
        serviceConnector = new ServiceConnector(this, this);                             // FIX-015

        buttonApplyStop.setOnClickListener(view -> applyLocation());
        buttonSettings.setOnClickListener(view -> showSettingsSheet());
        final View moreButton = findViewById(R.id.more_btn);
        if (moreButton != null) moreButton.setOnClickListener(this::showOverflowMenu);
        final View myLocationButton = findViewById(R.id.my_location_fab);
        if (myLocationButton != null) myLocationButton.setOnClickListener(v -> setLatLng(lat, lng, LOAD));

        wireBookmarkButtons();
        wireDetectionButton();
        wireQuickSettingsChips();

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
    protected void onStart() {
        super.onStart();
        context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefListener);
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
        updateQuickSettingsChips();
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
        buttonApplyStop.setIconResource(R.drawable.ic_location_on);
        buttonApplyStop.clearAnimation();
        tintButton(buttonApplyStop, R.color.colorPrimary, R.color.colorOnPrimary);
        updateStatusChip(false);
        buttonApplyStop.setOnClickListener(view -> applyLocation());
    }
    void changeButtonToStop() {
        buttonApplyStop.setText(context.getResources().getString(R.string.ActivityMain_Stop));
        buttonApplyStop.setIconResource(R.drawable.ic_stop_circle);
        tintButton(buttonApplyStop, R.color.colorError, R.color.colorOnError);
        buttonApplyStop.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_pulse));
        updateStatusChip(true);
        buttonApplyStop.announceForAccessibility(getString(R.string.A11y_MockActive,
                DECIMAL_FORMAT.format(lat) + ", " + DECIMAL_FORMAT.format(lng)));
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
        updateLocationMetadata();
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
            case MOCK_ERROR -> {
                endTime = 0;
                saveSettings();
                PermissionChecker.showDevSettingsDialog(this);                            // FIX-008
            }
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
        final View content = getLayoutInflater().inflate(R.layout.dialog_add_bookmark, null, false);
        final EditText input = content.findViewById(R.id.bookmark_name_input);
        final EditText latInput = content.findViewById(R.id.bookmark_lat_input);
        final EditText lngInput = content.findViewById(R.id.bookmark_lng_input);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        latInput.setText(DECIMAL_FORMAT.format(lat));
        lngInput.setText(DECIMAL_FORMAT.format(lng));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.Bookmark_Dialog_Title_Add)
                .setView(content)
                .setPositiveButton(R.string.Bookmark_Dialog_Save, (d, w) -> {
                    final String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    final double bookmarkLat = Inputs.parseDoubleSafe(latInput.getText().toString(), Double.NaN);
                    final double bookmarkLng = Inputs.parseDoubleSafe(lngInput.getText().toString(), Double.NaN);
                    if (!Inputs.isFinite(bookmarkLat) || !Inputs.isFinite(bookmarkLng)) {
                        showError(R.string.MainActivity_NoLatLong);
                        return;
                    }
                    final cl.coders.faketraveler.db.BookmarkEntity e =
                            new cl.coders.faketraveler.db.BookmarkEntity();
                    e.name = name;
                    e.lat = Inputs.clampLat(bookmarkLat);
                    e.lng = Inputs.clampLng(bookmarkLng);
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
                .setNegativeButton(R.string.Bookmark_Dialog_Cancel, null)
                .show();
    }

    private void showBookmarksSheet() {
        new cl.coders.faketraveler.ui.BookmarksBottomSheet()
                .show(getSupportFragmentManager(), "bookmarks");
    }

    // --- BookmarksBottomSheet.Host ---

    @Override
    public void onBookmarkSelected(@NonNull cl.coders.faketraveler.db.BookmarkEntity fav) {
        lat = fav.lat;
        lng = fav.lng;
        zoom = fav.zoom;
        setLatLng(lat, lng, SourceChange.LOAD);
        applyLocation();
        MockLogger.log("bookmark_apply", fav.name);
    }

    @Override
    public void onAddCurrentRequested() {
        showSaveBookmarkDialog();
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

    private void wireQuickSettingsChips() {
        final int[] chipIds = {
                R.id.quick_frequency_chip, R.id.quick_count_chip, R.id.quick_accuracy_chip
        };
        for (int id : chipIds) {
            final View chip = findViewById(id);
            if (chip != null) chip.setOnClickListener(v -> showSettingsSheet());
        }
        updateQuickSettingsChips();
    }

    private void updateQuickSettingsChips() {
        final TextView frequency = findViewById(R.id.quick_frequency_chip);
        final TextView count = findViewById(R.id.quick_count_chip);
        if (frequency != null) {
            frequency.setText(getString(R.string.QuickSettings_Frequency, mockFrequency));
        }
        if (count != null) {
            count.setText(mockCount == 0
                    ? getString(R.string.QuickSettings_Count_Infinite)
                    : getString(R.string.QuickSettings_Count_Finite, mockCount));
        }
    }

    private void updateLocationMetadata() {
        if (locationMetadata == null) return;
        locationMetadata.setText(DECIMAL_FORMAT.format(lat) + ", " + DECIMAL_FORMAT.format(lng));
    }

    private void updateStatusChip(boolean active) {
        if (statusChip == null) return;
        final int bgColor = active ? R.color.colorPrimaryContainer : R.color.colorSurfaceVariant;
        final int fgColor = active ? R.color.colorPrimary : R.color.colorSecondary;
        final int fg = ContextCompat.getColor(this, fgColor);
        statusChip.setText(active ? R.string.Status_Active : R.string.Status_Idle);
        statusChip.setChipIconResource(active ? R.drawable.ic_location_on : R.drawable.ic_location_off);
        statusChip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, bgColor)));
        statusChip.setTextColor(fg);
        statusChip.setChipIconTint(ColorStateList.valueOf(fg));
    }

    private static void tintButton(@NonNull MaterialButton button, int bgColor, int fgColor) {
        final int bg = ContextCompat.getColor(button.getContext(), bgColor);
        final int fg = ContextCompat.getColor(button.getContext(), fgColor);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setIconTint(ColorStateList.valueOf(fg));
    }

    private void showOverflowMenu(@NonNull View anchor) {
        final PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_main);
        menu.setOnMenuItemClickListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == R.id.action_bookmarks) {
                showBookmarksSheet();
                return true;
            }
            if (itemId == R.id.action_settings) {
                showSettingsSheet();
                return true;
            }
            if (itemId == R.id.action_about || itemId == R.id.action_help) {
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
    }
}

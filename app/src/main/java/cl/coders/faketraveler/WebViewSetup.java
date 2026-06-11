package cl.coders.faketraveler;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

/** WebView configuration + tile-Referer interceptor. FIX-007. */
public final class WebViewSetup {

    @NonNull
    private static final String TAG = WebViewSetup.class.getSimpleName();

    private WebViewSetup() {
        throw new UnsupportedOperationException();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    public static void configure(@NonNull WebView v, @NonNull WebAppInterface bridge) {
        final WebSettings s = v.getSettings();
        s.setJavaScriptEnabled(true);
        // Map page never calls window.open(); turning this off shrinks the attack surface.
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // setAllowFileAccess(false) blocks regular file:// URLs but does NOT block
        // file:///android_asset/ — the assets handler is special-cased in the framework
        // on every supported API level (21+). map.html loaded from android_asset still
        // resolves; the bundled tile fetcher uses HttpURLConnection, not WebView IO.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        // Deprecated since API 30 (off by default) — still called explicitly so older OEM
        // images that ignore the framework default cannot relax cross-origin from file://.
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        v.setWebChromeClient(new WebChromeClient());
        v.setWebViewClient(new FakeTravelerWebViewClient());
        v.addJavascriptInterface(bridge, "Android");
    }

    /**
     * WebViewClient subclass: intercepts whitelisted tile-server hosts to inject a stable
     * {@code Referer} header (some OSM-style providers 403 without one), and blocks
     * navigation to non-asset URLs.
     */
    static class FakeTravelerWebViewClient extends WebViewClient {

        @NonNull
        private static final Set<String> TILE_HOSTS = Set.of(
                "tile.openstreetmap.org",
                "a.tile.openstreetmap.org", "b.tile.openstreetmap.org", "c.tile.openstreetmap.org",
                "tile.openstreetmap.de",
                "a.tile.openstreetmap.de", "b.tile.openstreetmap.de", "c.tile.openstreetmap.de",
                "tile.osm.ch",
                "basemaps.cartocdn.com",
                "a.basemaps.cartocdn.com", "b.basemaps.cartocdn.com",
                "c.basemaps.cartocdn.com", "d.basemaps.cartocdn.com",
                "maps.wikimedia.org");

        @NonNull
        private static final String REFERER = "https://faketraveler.app/";

        @NonNull
        private static final String USER_AGENT = "FakeTraveler/2.4 (OSM tile loader)";

        private static final int TIMEOUT_MS = 8_000;

        // Cap on a single buffered tile so a misbehaving/oversized response cannot OOM the
        // WebView process. Real OSM-style PNG/JPEG tiles are well under this.
        private static final int MAX_TILE_BYTES = 4 * 1024 * 1024;

        @Override
        @Nullable
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest req) {
            final Uri u = req.getUrl();
            if (u == null) return null;
            final String host = u.getHost();
            if (host == null || !TILE_HOSTS.contains(host)) return null;
            HttpURLConnection conn = null;
            try {
                final URL url = new URL(u.toString());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Referer", REFERER);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setInstanceFollowRedirects(true);
                final int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    return null;
                }
                String mime = conn.getContentType();
                if (mime == null) mime = "image/png";
                final String encoding = conn.getContentEncoding();
                // Buffer the body fully so the connection can be released in finally; handing
                // the live stream to WebResourceResponse would leak the connection (no close).
                final byte[] body = readBounded(conn.getInputStream());
                return new WebResourceResponse(stripCharset(mime), encoding,
                        new ByteArrayInputStream(body));
            } catch (IOException e) {
                Log.w(TAG, "Tile interceptor falling back for " + u, e);
                return null;
            } catch (Throwable t) {
                Log.e(TAG, "Tile interceptor unexpected error for " + u, t);
                return null;
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) {}
                }
            }
        }

        @NonNull
        private static byte[] readBounded(@NonNull InputStream in) throws IOException {
            try (InputStream src = in) {
                final ByteArrayOutputStream buf = new ByteArrayOutputStream();
                final byte[] chunk = new byte[8192];
                int total = 0;
                int n;
                while ((n = src.read(chunk)) > 0) {
                    total += n;
                    if (total > MAX_TILE_BYTES) {
                        throw new IOException("Tile exceeds " + MAX_TILE_BYTES + " bytes");
                    }
                    buf.write(chunk, 0, n);
                }
                return buf.toByteArray();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView v, @NonNull WebResourceRequest r) {
            final String scheme = r.getUrl() != null ? r.getUrl().getScheme() : null;
            // Block navigation away from asset bundle; tile fetches still go through
            // shouldInterceptRequest because those are subresource requests, not navigations.
            return !"file".equals(scheme)
                    && !"about".equals(scheme)
                    && !"javascript".equals(scheme);
        }

        @NonNull
        private static String stripCharset(@NonNull String mime) {
            final int i = mime.indexOf(';');
            return i < 0 ? mime : mime.substring(0, i).trim();
        }
    }
}

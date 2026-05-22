package cl.coders.faketraveler;

import android.net.Uri;

import androidx.annotation.Nullable;

import cl.coders.faketraveler.util.Inputs;

public record GeoUri(double lat, double lng, @Nullable Double zoom) {

    /** Parse a {@code geo:lat,lng[?z=zoom]} URI. Returns {@code null} on any malformed
     *  field — never throws. Untrusted-input contract: every numeric component is
     *  parsed with {@link Inputs#parseDoubleSafe} and range-checked. */
    @Nullable
    public static GeoUri parse(@Nullable String geoUri) {
        if (geoUri == null) return null;
        final Uri uri;
        try {
            uri = Uri.parse(geoUri);
        } catch (Throwable t) {
            return null;
        }
        if (!"geo".equals(uri.getScheme())) return null;

        final String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) return null;
        final String[] split = ssp.split("\\?", 2);
        if (split.length < 1) return null;

        final String[] latLng = split[0].split(",", 2);
        if (latLng.length < 2) return null;

        final double lat = Inputs.parseDoubleSafe(latLng[0], Double.NaN);
        final double lng = Inputs.parseDoubleSafe(latLng[1], Double.NaN);
        if (!Inputs.validLat(lat) || !Inputs.validLng(lng)) return null;

        if (split.length < 2) return new GeoUri(lat, lng, null);
        Double zoom = null;
        for (String q : split[1].split("&")) {
            if (q.startsWith("z=")) {
                final double z = Inputs.parseDoubleSafe(q.substring(2), Double.NaN);
                if (Inputs.isFinite(z)) zoom = z;
            }
        }
        return new GeoUri(lat, lng, zoom);
    }

}

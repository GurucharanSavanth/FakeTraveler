package cl.coders.faketraveler;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class MapProviderUtil {

    public static final String DEFAULT_PROVIDER = "CartoDB.Positron"; // FIX-007

    private MapProviderUtil() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    public static String getDefaultMapProvider(@NonNull Locale locale) {
        final String lang = locale.getLanguage();
        if ("de".equals(lang)) return "OpenStreetMap.DE";
        if ("fr".equals(lang)) return "OpenStreetMap.France";
        return DEFAULT_PROVIDER;
    }
}

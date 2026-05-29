package cl.coders.faketraveler;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import cl.coders.faketraveler.db.ExifCleanedFileDao;
import cl.coders.faketraveler.db.ExifCleanedFileEntity;
import cl.coders.faketraveler.db.ModuleRepository;

/**
 * Module 6: strips GPS EXIF tags from device photos in place via a "rw" {@link MediaStore} file
 * descriptor (no raw paths, scoped-storage compliant). On API 29+ a write the app does not own
 * throws {@link android.app.RecoverableSecurityException}; in a background context we cannot launch
 * the consent {@code IntentSender}, so those are counted as {@code needsConsent} and skipped — the
 * UI documents that interactive consent is required for third-party photos.
 *
 * <p>Backups are not created (the schema's backup fields stay false), so cleaning is destructive of
 * the GPS tags. That is the point of the feature; the row records the original coordinates.
 */
public final class ExifCleaner {

    private static final String TAG = "ExifCleaner";

    private static final String[] GPS_TAGS = {
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD
    };

    public static final class CleanResult {
        public int scanned;
        public int cleaned;
        public int needsConsent;
    }

    @NonNull private final ContentResolver resolver;
    @NonNull private final ExifCleanedFileDao dao;

    public ExifCleaner(@NonNull Context ctx) {
        final Context app = ctx.getApplicationContext();
        this.resolver = app.getContentResolver();
        this.dao = ModuleRepository.get(app).getExifCleanedDao();
    }

    @WorkerThread
    @NonNull
    public CleanResult cleanAll(int limit) {
        final CleanResult res = new CleanResult();
        final Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final String[] proj = {MediaStore.Images.Media._ID};
        try (Cursor c = resolver.query(collection, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c == null) return res;
            final int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (c.moveToNext() && res.scanned < limit) {
                res.scanned++;
                final Uri uri = ContentUris.withAppendedId(collection, c.getLong(idCol));
                cleanOne(uri, res);
            }
        } catch (Throwable t) {
            Log.e(TAG, "cleanAll failed", t);
        }
        return res;
    }

    private void cleanOne(@NonNull Uri uri, @NonNull CleanResult res) {
        final float[] out = new float[2];
        boolean hasGps = false;
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null) return;
            // platform ExifInterface: boolean getLatLong(float[2] out), not the androidx double[] form
            hasGps = new ExifInterface(pfd.getFileDescriptor()).getLatLong(out);
        } catch (Throwable ignored) {
        }
        if (!hasGps) return; // no GPS to strip

        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "rw")) {
            if (pfd == null) return;
            final ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
            final double altitude = exif.getAltitude(0d);
            for (String tag : GPS_TAGS) exif.setAttribute(tag, null);
            exif.saveAttributes();

            final ExifCleanedFileEntity e = new ExifCleanedFileEntity();
            e.filePath = uri.toString();
            e.cleanedAt = System.currentTimeMillis();
            e.hadGpsData = true;
            e.originalLat = out[0];
            e.originalLng = out[1];
            e.originalAltitude = altitude;
            e.backupCreated = false;
            dao.insert(e);
            res.cleaned++;
        } catch (SecurityException se) {
            // API 29+ RecoverableSecurityException for non-owned media: needs interactive consent.
            res.needsConsent++;
        } catch (Throwable t) {
            Log.w(TAG, "clean failed for " + uri, t);
        }
    }
}

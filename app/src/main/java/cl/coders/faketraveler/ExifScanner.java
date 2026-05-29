package cl.coders.faketraveler;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Module 6: read-only audit of device photos for GPS EXIF tags via {@link MediaStore}. Reads each
 * image through a "r" file descriptor (works under scoped storage on API 29+ and on legacy storage)
 * so it never needs a raw file path. Mutating strips live in {@link ExifCleaner}.
 */
public final class ExifScanner {

    public static final class ScanResult {
        public int scanned;
        public int withGps;
    }

    @NonNull private final ContentResolver resolver;

    public ExifScanner(@NonNull Context ctx) {
        this.resolver = ctx.getApplicationContext().getContentResolver();
    }

    @WorkerThread
    @NonNull
    public ScanResult audit(int limit) {
        final ScanResult res = new ScanResult();
        final Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final String[] proj = {MediaStore.Images.Media._ID};
        try (Cursor c = resolver.query(collection, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c == null) return res;
            final int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (c.moveToNext() && res.scanned < limit) {
                res.scanned++;
                final Uri uri = ContentUris.withAppendedId(collection, c.getLong(idCol));
                if (hasGps(uri)) res.withGps++;
            }
        } catch (Throwable ignored) {
        }
        return res;
    }

    boolean hasGps(@NonNull Uri uri) {
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null) return false;
            final ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
            final float[] out = new float[2];
            return exif.getLatLong(out); // platform ExifInterface: boolean + float[2] out-param
        } catch (Throwable t) {
            return false;
        }
    }
}

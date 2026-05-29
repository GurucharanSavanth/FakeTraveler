package cl.coders.faketraveler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.db.EvidenceReportEntity;
import cl.coders.faketraveler.db.ExifCleanedFileEntity;
import cl.coders.faketraveler.db.GeoFenceEventEntity;
import cl.coders.faketraveler.db.MockSessionEntity;
import cl.coders.faketraveler.db.ModuleRepository;
import cl.coders.faketraveler.db.PermissionSnapshotEntity;
import cl.coders.faketraveler.db.PrivacyWipeLogEntity;
import cl.coders.faketraveler.db.SavedRouteEntity;

/**
 * Module 8: aggregates selected module data into a JSON, CSV, or PDF report under
 * {@code getExternalFilesDir(DOCUMENTS)} (app-private, no storage permission), records a
 * {@link EvidenceReportEntity} with a SHA-256 of the file for integrity. Module 4 (network) was
 * dropped, so {@code includesNetwork} is always recorded false.
 */
public final class EvidenceExporter {

    private static final String TAG = "EvidenceExporter";

    public static final class Options {
        public boolean sessions, routes, geofences, permissions, exif, wipes;
        public String format = "json"; // json | csv | pdf
        public long dateFrom, dateTo, sessionId;
        boolean inRange(long ts) {
            return (dateFrom <= 0 || ts >= dateFrom) && (dateTo <= 0 || ts <= dateTo);
        }
    }

    @NonNull private final Context appCtx;
    @NonNull private final ModuleRepository repo;

    public EvidenceExporter(@NonNull Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.repo = ModuleRepository.get(appCtx);
    }

    @WorkerThread
    @NonNull
    public EvidenceReportEntity export(@NonNull Options o) throws IOException {
        final long now = System.currentTimeMillis();
        final String ext = "csv".equals(o.format) || "pdf".equals(o.format) ? o.format : "json";
        File dir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = appCtx.getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        final File out = new File(dir, "evidence_" + now + "." + ext);

        switch (ext) {
            case "csv": writeCsv(out, o); break;
            case "pdf": writePdf(out, o); break;
            default:    writeJson(out, o); break;
        }

        final EvidenceReportEntity r = new EvidenceReportEntity();
        r.generatedAt = now;
        r.reportType = ext;
        r.filePath = out.getAbsolutePath();
        r.fileSizeBytes = out.length();
        r.checksumSha256 = sha256(out);
        r.sessionId = o.sessionId;
        r.dateFrom = o.dateFrom;
        r.dateTo = o.dateTo;
        r.includesSessions = o.sessions;
        r.includesRoutes = o.routes;
        r.includesGeoFences = o.geofences;
        r.includesNetwork = false;
        r.includesPermissions = o.permissions;
        r.includesExif = o.exif;
        r.includesWipes = o.wipes;
        repo.getEvidenceReportDao().insert(r);
        return r;
    }

    // ---- JSON ----------------------------------------------------------------

    private void writeJson(@NonNull File out, @NonNull Options o) throws IOException {
        final JSONObject root = new JSONObject();
        try {
            root.put("generatedAt", System.currentTimeMillis());
            root.put("app", appCtx.getPackageName());
            if (o.sessions) root.put("sessions", sessionsJson(o));
            if (o.routes) root.put("routes", routesJson());
            if (o.geofences) root.put("geofenceEvents", geofenceJson(o));
            if (o.permissions) root.put("permissions", permissionsJson());
            if (o.exif) root.put("exif", exifJson());
            if (o.wipes) root.put("wipes", wipesJson(o));
        } catch (Throwable t) {
            Log.w(TAG, "json build", t);
        }
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(root.toString(2).getBytes("UTF-8"));
        } catch (Throwable t) {
            throw new IOException("json write failed", t);
        }
    }

    private JSONArray sessionsJson(@NonNull Options o) throws Exception {
        final JSONArray arr = new JSONArray();
        for (MockSessionEntity s : repo.getMockSessionDao().getAllSessionsSync()) {
            if (!o.inRange(s.startTime)) continue;
            if (o.sessionId > 0 && s.id != o.sessionId) continue;
            final JSONObject j = new JSONObject();
            j.put("id", s.id); j.put("label", s.sessionLabel);
            j.put("startTime", s.startTime); j.put("endTime", s.endTime);
            j.put("startLat", s.startLat); j.put("startLng", s.startLng);
            j.put("endLat", s.endLat); j.put("endLng", s.endLng);
            j.put("points", s.mockCount); j.put("completed", s.completed);
            arr.put(j);
        }
        return arr;
    }

    private JSONArray routesJson() throws Exception {
        final JSONArray arr = new JSONArray();
        for (SavedRouteEntity r : repo.getRouteDao().getAllRoutesSync()) {
            final JSONObject j = new JSONObject();
            j.put("id", r.id); j.put("name", r.name);
            j.put("points", r.pointCount); j.put("distanceMeters", r.totalDistanceMeters);
            j.put("durationSeconds", r.estimatedDurationSeconds); j.put("createdAt", r.createdAt);
            arr.put(j);
        }
        return arr;
    }

    private JSONArray geofenceJson(@NonNull Options o) throws Exception {
        final JSONArray arr = new JSONArray();
        for (GeoFenceEventEntity e : repo.getGeoFenceDao().getAllEventsSync()) {
            if (!o.inRange(e.timestamp)) continue;
            final JSONObject j = new JSONObject();
            j.put("fenceId", e.geofenceId); j.put("type", e.eventType);
            j.put("timestamp", e.timestamp); j.put("lat", e.triggeredLat);
            j.put("lng", e.triggeredLng); j.put("sessionId", e.sessionId);
            arr.put(j);
        }
        return arr;
    }

    private JSONArray permissionsJson() throws Exception {
        final JSONArray arr = new JSONArray();
        final long ts = repo.getPermissionSnapshotDao().getLatestTimestamp();
        if (ts <= 0) return arr;
        for (PermissionSnapshotEntity s : repo.getPermissionSnapshotDao().getSnapshotsAt(ts)) {
            final JSONObject j = new JSONObject();
            j.put("package", s.packageName); j.put("app", s.appName);
            j.put("permission", s.permission); j.put("status", s.status);
            j.put("dangerous", s.isDangerous);
            arr.put(j);
        }
        return arr;
    }

    private JSONArray exifJson() throws Exception {
        final JSONArray arr = new JSONArray();
        for (ExifCleanedFileEntity f : repo.getExifCleanedDao().getAllCleanedFilesSync()) {
            final JSONObject j = new JSONObject();
            j.put("file", f.filePath); j.put("cleanedAt", f.cleanedAt);
            j.put("hadGps", f.hadGpsData); j.put("lat", f.originalLat); j.put("lng", f.originalLng);
            arr.put(j);
        }
        return arr;
    }

    private JSONArray wipesJson(@NonNull Options o) throws Exception {
        final JSONArray arr = new JSONArray();
        for (PrivacyWipeLogEntity w : repo.getWipeLogDao().getAllWipesSync()) {
            if (!o.inRange(w.wipedAt)) continue;
            final JSONObject j = new JSONObject();
            j.put("wipedAt", w.wipedAt); j.put("type", w.wipeType);
            j.put("success", w.success); j.put("details", w.detailsJson);
            arr.put(j);
        }
        return arr;
    }

    // ---- CSV -----------------------------------------------------------------

    private void writeCsv(@NonNull File out, @NonNull Options o) throws IOException {
        final StringBuilder sb = new StringBuilder();
        if (o.sessions) {
            sb.append("# sessions\nid,label,startTime,endTime,points,completed\n");
            for (MockSessionEntity s : repo.getMockSessionDao().getAllSessionsSync()) {
                if (!o.inRange(s.startTime)) continue;
                if (o.sessionId > 0 && s.id != o.sessionId) continue;
                sb.append(s.id).append(',').append(csv(s.sessionLabel)).append(',')
                        .append(s.startTime).append(',').append(s.endTime).append(',')
                        .append(s.mockCount).append(',').append(s.completed).append('\n');
            }
        }
        if (o.geofences) {
            sb.append("# geofence_events\nfenceId,type,timestamp,lat,lng,sessionId\n");
            for (GeoFenceEventEntity e : repo.getGeoFenceDao().getAllEventsSync()) {
                if (!o.inRange(e.timestamp)) continue;
                sb.append(e.geofenceId).append(',').append(e.eventType).append(',')
                        .append(e.timestamp).append(',')
                        .append(String.format(Locale.US, "%.6f", e.triggeredLat)).append(',')
                        .append(String.format(Locale.US, "%.6f", e.triggeredLng)).append(',')
                        .append(e.sessionId).append('\n');
            }
        }
        if (o.permissions) {
            sb.append("# permissions\npackage,permission,status,dangerous\n");
            final long ts = repo.getPermissionSnapshotDao().getLatestTimestamp();
            if (ts > 0) for (PermissionSnapshotEntity s : repo.getPermissionSnapshotDao().getSnapshotsAt(ts)) {
                sb.append(csv(s.packageName)).append(',').append(csv(s.permission)).append(',')
                        .append(s.status).append(',').append(s.isDangerous).append('\n');
            }
        }
        if (o.exif) {
            sb.append("# exif\nfile,cleanedAt,lat,lng\n");
            for (ExifCleanedFileEntity f : repo.getExifCleanedDao().getAllCleanedFilesSync()) {
                sb.append(csv(f.filePath)).append(',').append(f.cleanedAt).append(',')
                        .append(String.format(Locale.US, "%.6f", f.originalLat)).append(',')
                        .append(String.format(Locale.US, "%.6f", f.originalLng)).append('\n');
            }
        }
        if (o.wipes) {
            sb.append("# wipes\nwipedAt,type,success\n");
            for (PrivacyWipeLogEntity w : repo.getWipeLogDao().getAllWipesSync()) {
                if (!o.inRange(w.wipedAt)) continue;
                sb.append(w.wipedAt).append(',').append(w.wipeType).append(',')
                        .append(w.success).append('\n');
            }
        }
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(sb.toString().getBytes("UTF-8"));
        }
    }

    // ---- PDF -----------------------------------------------------------------

    private void writePdf(@NonNull File out, @NonNull Options o) throws IOException {
        final PdfDocument doc = new PdfDocument();
        final Paint paint = new Paint();
        paint.setTextSize(10f);
        final int pageW = 595, pageH = 842, margin = 36;
        final int[] page = {1};
        final PdfDocument.PageInfo[] info = {new PdfDocument.PageInfo.Builder(pageW, pageH, page[0]).create()};
        PdfDocument.Page p = doc.startPage(info[0]);
        Canvas canvas = p.getCanvas();
        final float[] y = {margin};

        // header
        paint.setTextSize(16f);
        canvas.drawText("FakeTraveler Evidence Report", margin, y[0], paint);
        y[0] += 22;
        paint.setTextSize(9f);
        canvas.drawText("Generated " + new java.util.Date().toString(), margin, y[0], paint);
        y[0] += 18;
        paint.setTextSize(10f);

        final List<String> lines = new ArrayList<>();
        if (o.sessions) {
            lines.add("== Sessions ==");
            for (MockSessionEntity s : repo.getMockSessionDao().getAllSessionsSync()) {
                if (!o.inRange(s.startTime)) continue;
                lines.add("#" + s.id + " " + safe(s.sessionLabel) + " pts=" + s.mockCount
                        + " ok=" + s.completed);
            }
        }
        if (o.geofences) {
            lines.add("== GeoFence events ==");
            for (GeoFenceEventEntity e : repo.getGeoFenceDao().getAllEventsSync()) {
                if (!o.inRange(e.timestamp)) continue;
                lines.add("fence=" + e.geofenceId + " type=" + e.eventType + " @"
                        + String.format(Locale.US, "%.5f,%.5f", e.triggeredLat, e.triggeredLng));
            }
        }
        if (o.permissions) {
            lines.add("== Permissions (latest snapshot) ==");
            final long ts = repo.getPermissionSnapshotDao().getLatestTimestamp();
            if (ts > 0) for (PermissionSnapshotEntity s : repo.getPermissionSnapshotDao().getSnapshotsAt(ts)) {
                lines.add(safe(s.packageName) + " : " + shortPerm(s.permission)
                        + (s.status == 1 ? " [granted]" : " [denied]"));
            }
        }
        if (o.exif) {
            lines.add("== EXIF cleaned ==");
            for (ExifCleanedFileEntity f : repo.getExifCleanedDao().getAllCleanedFilesSync()) {
                lines.add(safe(f.filePath));
            }
        }
        if (o.wipes) {
            lines.add("== Wipes ==");
            for (PrivacyWipeLogEntity w : repo.getWipeLogDao().getAllWipesSync()) {
                if (!o.inRange(w.wipedAt)) continue;
                lines.add("type=" + w.wipeType + " ok=" + w.success);
            }
        }

        for (String line : lines) {
            if (y[0] > pageH - margin) {
                doc.finishPage(p);
                page[0]++;
                info[0] = new PdfDocument.PageInfo.Builder(pageW, pageH, page[0]).create();
                p = doc.startPage(info[0]);
                canvas = p.getCanvas();
                y[0] = margin;
            }
            canvas.drawText(clip(line), margin, y[0], paint);
            y[0] += 14;
        }
        doc.finishPage(p);
        try (OutputStream os = new FileOutputStream(out)) {
            doc.writeTo(os);
        } finally {
            doc.close();
        }
    }

    // ---- helpers -------------------------------------------------------------

    @NonNull
    private static String sha256(@NonNull File f) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                final byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            final StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    @NonNull
    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @NonNull private static String safe(String s) { return s == null ? "" : s; }

    @NonNull
    private static String shortPerm(@NonNull String p) {
        final int dot = p.lastIndexOf('.');
        return dot >= 0 && dot < p.length() - 1 ? p.substring(dot + 1) : p;
    }

    @NonNull
    private static String clip(@NonNull String s) {
        return s.length() > 110 ? s.substring(0, 107) + "..." : s;
    }
}

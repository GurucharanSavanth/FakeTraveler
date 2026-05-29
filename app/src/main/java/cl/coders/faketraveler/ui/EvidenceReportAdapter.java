package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.EvidenceReportEntity;

/** RecyclerView adapter for {@link cl.coders.faketraveler.EvidenceExportActivity} (Module 8). */
public class EvidenceReportAdapter extends RecyclerView.Adapter<EvidenceReportAdapter.VH> {

    public interface Listener {
        void onShare(@NonNull EvidenceReportEntity report);
        void onDelete(@NonNull EvidenceReportEntity report);
    }

    @NonNull private final List<EvidenceReportEntity> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public EvidenceReportAdapter(@NonNull Listener listener) { this.listener = listener; }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<EvidenceReportEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_evidence_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        EvidenceReportEntity r = items.get(pos);
        h.title.setText(String.format(Locale.US, "%s · %s",
                r.reportType == null ? "?" : r.reportType.toUpperCase(Locale.US),
                formatSize(r.fileSizeBytes)));
        h.meta.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(r.generatedAt)));
        h.checksum.setText(r.checksumSha256 == null || r.checksumSha256.length() < 12
                ? "" : "sha256: " + r.checksumSha256.substring(0, 12) + "…");
        h.share.setOnClickListener(v -> listener.onShare(r));
        h.delete.setOnClickListener(v -> listener.onDelete(r));
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView checksum;
        final MaterialButton share;
        final MaterialButton delete;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.evidence_item_title);
            meta = v.findViewById(R.id.evidence_item_meta);
            checksum = v.findViewById(R.id.evidence_item_checksum);
            share = v.findViewById(R.id.evidence_item_share);
            delete = v.findViewById(R.id.evidence_item_delete);
        }
    }
}

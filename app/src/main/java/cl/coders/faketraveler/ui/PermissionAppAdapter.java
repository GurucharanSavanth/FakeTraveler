package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.R;

/** RecyclerView adapter for {@link cl.coders.faketraveler.PermissionDriftActivity} (Module 5). */
public class PermissionAppAdapter extends RecyclerView.Adapter<PermissionAppAdapter.VH> {

    /** Aggregated per-app row built by the activity from the latest snapshot + open alerts. */
    public static final class AppRow {
        public String packageName = "";
        public String appName = "";
        public int granted;
        public int total;
        public int dangerous;
        public int alertCount;
    }

    public interface Listener {
        void onApp(@NonNull AppRow row);
    }

    @NonNull private final List<AppRow> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public PermissionAppAdapter(@NonNull Listener listener) { this.listener = listener; }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<AppRow> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_permission_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AppRow r = items.get(pos);
        h.name.setText(r.appName == null || r.appName.isEmpty() ? r.packageName : r.appName);
        h.meta.setText(String.format(Locale.US, "%d/%d granted · %d dangerous",
                r.granted, r.total, r.dangerous));
        if (r.alertCount > 0) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(String.valueOf(r.alertCount));
        } else {
            h.badge.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onApp(r));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView badge;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.permission_app_name);
            meta = v.findViewById(R.id.permission_app_meta);
            badge = v.findViewById(R.id.permission_app_badge);
        }
    }
}

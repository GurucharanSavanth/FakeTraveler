package cl.coders.faketraveler.ui;

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
import cl.coders.faketraveler.db.MockSessionEntity;

/**
 * RecyclerView adapter for {@link cl.coders.faketraveler.ui.SessionHistoryBottomSheet} (Module 1).
 * Mirrors {@link BookmarkAdapter}: holds a private snapshot pushed in by the observing sheet, no DB
 * access in the view layer.
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

    public interface Listener {
        void onReplay(@NonNull MockSessionEntity session);
        void onLongPress(@NonNull MockSessionEntity session);
    }

    @NonNull private final List<MockSessionEntity> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public SessionAdapter(@NonNull Listener listener) { this.listener = listener; }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<MockSessionEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @androidx.annotation.Nullable
    public MockSessionEntity itemAt(int pos) {
        if (pos < 0 || pos >= items.size()) return null;
        return items.get(pos);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        MockSessionEntity s = items.get(pos);
        h.label.setText(s.sessionLabel == null || s.sessionLabel.isEmpty()
                ? "Session " + s.id : s.sessionLabel);
        h.meta.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(s.startTime)));
        h.duration.setText(h.itemView.getContext().getString(
                R.string.SessionHistory_Item_Summary,
                formatDuration(s.endTime - s.startTime), s.mockCount));
        h.replay.setOnClickListener(v -> listener.onReplay(s));
        h.itemView.setOnLongClickListener(v -> { listener.onLongPress(s); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    private static String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0) return String.format(Locale.US, "%dh %dm", h, m);
        if (m > 0) return String.format(Locale.US, "%dm %ds", m, s);
        return String.format(Locale.US, "%ds", s);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView meta;
        final TextView duration;
        final MaterialButton replay;
        VH(@NonNull View v) {
            super(v);
            label = v.findViewById(R.id.session_label);
            meta = v.findViewById(R.id.session_meta);
            duration = v.findViewById(R.id.session_duration);
            replay = v.findViewById(R.id.session_replay_btn);
        }
    }
}

package cl.coders.faketraveler.debug;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.R;

/** RecyclerView adapter for the debug console log list. */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {

    @NonNull private final List<MockLogger.Entry> items = new ArrayList<>();
    @NonNull private final SimpleDateFormat sdf =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    @android.annotation.SuppressLint("NotifyDataSetChanged")  // bulk refill (init, clear)
    public void submit(@NonNull List<MockLogger.Entry> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    public void append(@NonNull MockLogger.Entry e) {
        items.add(e);
        notifyItemInserted(items.size() - 1);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View row = LayoutInflater.from(p.getContext()).inflate(R.layout.item_log_entry, p, false);
        return new VH(row);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        MockLogger.Entry e = items.get(pos);
        h.ts.setText(sdf.format(new Date(e.ts)));
        h.level.setText(e.level);
        h.message.setText(e.message);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView ts, level, message;
        VH(@NonNull View v) {
            super(v);
            ts = v.findViewById(R.id.log_ts);
            level = v.findViewById(R.id.log_level);
            message = v.findViewById(R.id.log_msg);
        }
    }
}

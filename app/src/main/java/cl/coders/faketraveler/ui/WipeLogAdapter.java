package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.PrivacyWipeLogEntity;

/** RecyclerView adapter for the wipe history in {@link PrivacyWipeBottomSheet} (Module 7). */
public class WipeLogAdapter extends RecyclerView.Adapter<WipeLogAdapter.VH> {

    @NonNull private final List<PrivacyWipeLogEntity> items = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<PrivacyWipeLogEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wipe_log, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        PrivacyWipeLogEntity w = items.get(pos);
        h.title.setText(h.itemView.getContext().getString(
                w.wipeType == 6 ? R.string.PrivacyWipe_Type_Full : R.string.PrivacyWipe_Type_Partial));
        h.meta.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(w.wipedAt)));
        h.status.setText(h.itemView.getContext().getString(
                w.success ? R.string.PrivacyWipe_Status_Ok : R.string.PrivacyWipe_Status_Fail));
        h.status.setTextColor(ContextCompat.getColor(h.itemView.getContext(), w.success
                ? android.R.color.darker_gray
                : androidx.appcompat.R.color.error_color_material_light));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView status;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.wipe_log_title);
            meta = v.findViewById(R.id.wipe_log_meta);
            status = v.findViewById(R.id.wipe_log_status);
        }
    }
}

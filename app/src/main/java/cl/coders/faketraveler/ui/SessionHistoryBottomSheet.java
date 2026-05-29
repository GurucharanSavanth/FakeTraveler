package cl.coders.faketraveler.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.MockSessionEntity;

/**
 * Module 1: lists recorded {@link MockSessionEntity} runs. Tap-replay routes through the optional
 * {@link Host} (wired by MainActivity once Route Lab playback lands); long-press deletes a session
 * (CASCADE removes its route points). Mirrors {@link BookmarksBottomSheet}: observes the DAO's
 * LiveData and runs the single mutations on a short-lived background thread (Room forbids main).
 *
 * <p>{@link Host} is optional here (unlike Bookmarks) so the sheet works before replay is wired.
 */
public class SessionHistoryBottomSheet extends BottomSheetDialogFragment
        implements SessionAdapter.Listener {

    public interface Host {
        void onReplaySession(long sessionId);
    }

    @Nullable private Host host;
    @Nullable private SessionAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Host) host = (Host) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_session_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final View empty = view.findViewById(R.id.session_empty);
        final RecyclerView list = view.findViewById(R.id.session_list);
        final View clearAll = view.findViewById(R.id.session_clear_all_btn);
        if (clearAll != null) clearAll.setOnClickListener(v -> confirmClearAll());

        adapter = new SessionAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        AppDatabase.get(requireContext()).mockSessionDao().getAllSessions().observe(
                getViewLifecycleOwner(),
                sessions -> {
                    if (adapter != null) adapter.submit(sessions);
                    empty.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onReplay(@NonNull MockSessionEntity session) {
        if (host != null) host.onReplaySession(session.id);
        dismiss();
    }

    @Override
    public void onLongPress(@NonNull MockSessionEntity session) {
        if (!isAdded()) return;
        final Context appCtx = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.SessionHistory_Delete_Title)
                .setMessage(R.string.SessionHistory_Delete_Message)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> runDb(() -> AppDatabase.get(appCtx).mockSessionDao().deleteSession(session)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClearAll() {
        if (!isAdded()) return;
        final Context appCtx = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.SessionHistory_ClearAll_Title)
                .setMessage(R.string.SessionHistory_ClearAll_Message)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> runDb(() -> AppDatabase.get(appCtx).mockSessionDao().deleteAllSessions()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Single mutations only; bg thread holds application context so it cannot leak the fragment. */
    private static void runDb(@NonNull Runnable r) {
        final Thread t = new Thread(r, "SessionHistoryIO");
        t.setDaemon(true);
        t.start();
    }
}

package cl.coders.faketraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.debug.LogAdapter;
import cl.coders.faketraveler.util.Inputs;

/**
 * Bottom-sheet host for the live {@link MockLogger} ring buffer. Ports the original
 * {@code DebugConsoleActivity}: shows entries in a RecyclerView, exposes export +
 * clear + stress-test actions. The sheet is UI-unreachable until T10's 7-tap
 * gesture on About or T12 wires it from Settings; intended call site:
 * {@link #show(FragmentManager)}.
 */
public class DebugConsoleBottomSheet extends BottomSheetDialogFragment
        implements MockLogger.Listener {

    @Nullable private LogAdapter adapter;
    @Nullable private Thread stressThread;

    /** Open the sheet from any host. T12 wires this from {@code SettingsBottomSheet}. */
    public static void show(@NonNull FragmentManager fm) {
        new DebugConsoleBottomSheet().show(fm, "debug");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_debug_console, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView list = Inputs.requireView(view, R.id.log_list, "log_list");
        adapter = new LogAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        adapter.submit(MockLogger.snapshot());

        Inputs.<View>requireView(view, R.id.btn_export, "btn_export")
                .setOnClickListener(v -> exportLog());
        Inputs.<View>requireView(view, R.id.btn_clear, "btn_clear")
                .setOnClickListener(v -> {
            MockLogger.clear();
            if (adapter != null) adapter.submit(MockLogger.snapshot());
        });
        Inputs.<View>requireView(view, R.id.btn_stress, "btn_stress")
                .setOnClickListener(v -> {
            if (stressThread != null && stressThread.isAlive()) return;
            stressThread = new Thread(() -> {
                for (int i = 0; i < 1000 && !Thread.currentThread().isInterrupted(); i++) {
                    MockLogger.log("stress", "n=" + i);
                    try { Thread.sleep(10L); } catch (InterruptedException ignored) { return; }
                }
            }, "stress-test");
            stressThread.setDaemon(true);
            stressThread.start();
        });
    }

    @Override public void onStart() {
        super.onStart();
        MockLogger.addListener(this);
    }

    @Override public void onStop() {
        MockLogger.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (stressThread != null) stressThread.interrupt();
        adapter = null;
        super.onDestroyView();
    }

    @Override
    public void onEntry(@NonNull MockLogger.Entry e) {
        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || isRemoving()) return;
            final LogAdapter a = adapter;
            if (a != null) a.append(e);
        });
    }

    private void exportLog() {
        final String text = MockLogger.exportText();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.Debug_ExportTitle)));
    }
}

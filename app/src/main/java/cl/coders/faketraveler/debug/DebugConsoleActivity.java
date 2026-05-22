package cl.coders.faketraveler.debug;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import cl.coders.faketraveler.util.Inputs;

import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.R;

/**
 * Live view of the {@link MockLogger} ring buffer. Unlocked via the 7-tap gesture on the
 * version footer in {@code MoreActivity}. Offers an export (text) and a stress-test action
 * useful for shaking out concurrency bugs in the log path itself.
 */
public class DebugConsoleActivity extends AppCompatActivity implements MockLogger.Listener {

    @Nullable private LogAdapter adapter;
    @Nullable private Thread stressThread;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_console);

        final RecyclerView list = Inputs.requireView(this, R.id.log_list, "log_list");
        adapter = new LogAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        adapter.submit(MockLogger.snapshot());

        Inputs.<android.view.View>requireView(this, R.id.btn_export, "btn_export")
                .setOnClickListener(v -> exportLog());
        Inputs.<android.view.View>requireView(this, R.id.btn_clear, "btn_clear")
                .setOnClickListener(v -> {
            MockLogger.clear();
            if (adapter != null) adapter.submit(MockLogger.snapshot());
        });
        Inputs.<android.view.View>requireView(this, R.id.btn_stress, "btn_stress")
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

    @Override protected void onStart() {
        super.onStart();
        MockLogger.addListener(this);
    }

    @Override protected void onStop() {
        MockLogger.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (stressThread != null) stressThread.interrupt();
        adapter = null;
        super.onDestroy();
    }

    @Override
    public void onEntry(@NonNull MockLogger.Entry e) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
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

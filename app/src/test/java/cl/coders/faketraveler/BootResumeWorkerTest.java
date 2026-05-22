package cl.coders.faketraveler;

import static androidx.work.ListenableWorker.Result;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.testing.TestWorkerBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BootResumeWorkerTest {

    @Test public void worker_returns_success_when_endTime_in_future() {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                .edit()
                .putLong("endTime", System.currentTimeMillis() + 60_000L)
                .putBoolean("restoreAfterBoot", true)
                .apply();

        BootResumeWorker worker = TestWorkerBuilder.from(
                ctx, BootResumeWorker.class, Executors.newSingleThreadExecutor()).build();

        Result result = worker.doWork();
        assertTrue(result instanceof Result.Success);
    }

    @Test public void worker_returns_success_no_op_when_endTime_past() {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                .edit()
                .putLong("endTime", 1L)
                .putBoolean("restoreAfterBoot", true)
                .apply();

        BootResumeWorker worker = TestWorkerBuilder.from(
                ctx, BootResumeWorker.class, Executors.newSingleThreadExecutor()).build();

        Result result = worker.doWork();
        assertTrue(result instanceof Result.Success);
    }
}

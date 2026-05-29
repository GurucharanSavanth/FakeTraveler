package cl.coders.faketraveler.detection;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DetectionEngineTest {

    @Test public void run_returns_report_with_all_checks() {
        DetectionEngine.Report r = DetectionEngine.run(ApplicationProvider.getApplicationContext());
        assertTrue("expected >= 4 checks, got " + r.checks.size(), r.checks.size() >= 4);
        assertTrue(r.risk == DetectionEngine.Risk.LOW
                || r.risk == DetectionEngine.Risk.MEDIUM
                || r.risk == DetectionEngine.Risk.HIGH);
    }

    @Test public void each_check_has_label_and_detail() {
        DetectionEngine.Report r = DetectionEngine.run(ApplicationProvider.getApplicationContext());
        for (DetectionEngine.CheckResult c : r.checks) {
            assertTrue("empty label", !c.label.isEmpty());
            assertTrue("empty detail", !c.detail.isEmpty());
        }
    }
}

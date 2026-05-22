package cl.coders.faketraveler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OemBatteryOptHelperTest {

    @Test public void detect_xiaomi() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "Xiaomi");
        assertEquals(OemBatteryOptHelper.Vendor.XIAOMI, OemBatteryOptHelper.detect());
    }

    @Test public void detect_poco_as_xiaomi() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "POCO");
        assertEquals(OemBatteryOptHelper.Vendor.XIAOMI, OemBatteryOptHelper.detect());
    }

    @Test public void detect_redmi_as_xiaomi() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "Redmi");
        assertEquals(OemBatteryOptHelper.Vendor.XIAOMI, OemBatteryOptHelper.detect());
    }

    @Test public void detect_samsung() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "samsung");
        assertEquals(OemBatteryOptHelper.Vendor.SAMSUNG, OemBatteryOptHelper.detect());
    }

    @Test public void detect_iqoo_as_vivo() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "iQOO");
        assertEquals(OemBatteryOptHelper.Vendor.VIVO, OemBatteryOptHelper.detect());
    }

    @Test public void detect_oneplus() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "OnePlus");
        assertEquals(OemBatteryOptHelper.Vendor.ONEPLUS, OemBatteryOptHelper.detect());
    }

    @Test public void detect_realme() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "realme");
        assertEquals(OemBatteryOptHelper.Vendor.REALME, OemBatteryOptHelper.detect());
    }

    @Test public void detect_oppo() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "OPPO");
        assertEquals(OemBatteryOptHelper.Vendor.OPPO, OemBatteryOptHelper.detect());
    }

    @Test public void detect_honor_as_huawei() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "HONOR");
        assertEquals(OemBatteryOptHelper.Vendor.HUAWEI, OemBatteryOptHelper.detect());
    }

    @Test public void detect_unknown_falls_through() {
        ReflectionHelpers.setStaticField(android.os.Build.class, "MANUFACTURER", "NoSuchOem");
        assertEquals(OemBatteryOptHelper.Vendor.UNKNOWN, OemBatteryOptHelper.detect());
    }
}

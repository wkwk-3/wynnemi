package io.github.wkwk.wei;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogPolicyTest {
    @Test
    void prereleaseIsDecidedBeforeThePlus() {
        assertTrue(LogPolicy.isPrerelease("2.1.0-test.7+1.21.11"));
        assertTrue(LogPolicy.isPrerelease("2.1.0-test.7"));
        assertFalse(LogPolicy.isPrerelease("2.1.0+1.21.11"));
        // Minecraft のバージョン側の「-」はプレリリース扱いにしない
        assertFalse(LogPolicy.isPrerelease("2.1.0+1.21.11-rc1"));
    }

    @Test
    void systemPropertyWinsOverConfigWhichWinsOverVersion() {
        assertTrue(LogPolicy.enabled("true", false, false));
        assertFalse(LogPolicy.enabled("false", true, true));
        assertTrue(LogPolicy.enabled(null, true, false));
        assertFalse(LogPolicy.enabled(null, false, true));
        assertTrue(LogPolicy.enabled(null, null, true));
        assertFalse(LogPolicy.enabled(null, null, false));
    }
}

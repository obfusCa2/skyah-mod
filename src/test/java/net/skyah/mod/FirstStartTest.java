package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B as the deal hotkey only on a fresh install, only while unbound, never over another SkyAH key. */
class FirstStartTest {
    @Test
    void onlyFreshAndUnboundAndFree() {
        List<String> own = List.of("key.keyboard.k", "key.keyboard.g", "key.keyboard.h");
        assertTrue(FirstStart.bindDefaultHotkey(true, true, own));
        assertTrue(FirstStart.bindDefaultHotkey(true, true, null));
        assertFalse(FirstStart.bindDefaultHotkey(false, true, own));
        assertFalse(FirstStart.bindDefaultHotkey(true, false, own));
        assertFalse(FirstStart.bindDefaultHotkey(true, true, List.of("key.keyboard.k", "key.keyboard.b")));
    }
}

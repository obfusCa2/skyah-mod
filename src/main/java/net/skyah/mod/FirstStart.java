package net.skyah.mod;

import java.util.Collection;

/** What a fresh install (no config file at the first start) gets that an existing one does not, free of
 *  game classes so it can be tested: the classic chat line (DealLine.initialStyle) and B as the deal
 *  hotkey, the usual key for the next flip. Existing configs are never touched. */
final class FirstStart {
    /** The deal hotkey a fresh install starts with. */
    static final String DEFAULT_HOTKEY = "key.keyboard.b";

    private FirstStart() {
    }

    /** True when the deal hotkey should become B now: a fresh install, the hotkey still unbound, and B not
     *  taken by another SkyAH key (ownKeys: their saved key names, e.g. "key.keyboard.k"). */
    static boolean bindDefaultHotkey(boolean fresh, boolean unbound, Collection<String> ownKeys) {
        return fresh && unbound && (ownKeys == null || !ownKeys.contains(DEFAULT_HOTKEY));
    }
}

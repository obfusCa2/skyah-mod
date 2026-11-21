package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /skyah bl and /skyah wl edit the same comma separated lists as the menu; /skyah help lists the
 *  commands. */
class SkyAhCommandsTest {
    @Test
    void addAndRemoveKeepTheList() {
        assertEquals("hyperion", SkyAhCommands.add("", "hyperion"));
        assertEquals("hyperion, juju", SkyAhCommands.add(" hyperion ,", " juju "));
        assertEquals("hyperion, juju", SkyAhCommands.add("hyperion, juju", "JUJU"));
        assertEquals("juju", SkyAhCommands.remove("hyperion, juju", "Hyperion"));
        assertEquals("hyperion", SkyAhCommands.remove("hyperion", "dye"));
        assertEquals("", SkyAhCommands.add(null, " "));
        assertEquals(List.of("a", "b"), SkyAhCommands.entries("a,,b, "));
    }

    @Test
    void helpListsTheCommands() {
        assertTrue(SkyAhCommands.help().stream().noneMatch(l -> l.contains(" bz")));
        assertTrue(SkyAhCommands.help().stream().anyMatch(l -> l.startsWith("/skyah flip")));
        for (String l : SkyAhCommands.help()) {
            assertTrue(!l.contains("\u2014"), l);
        }
    }
}

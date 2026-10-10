package net.skyah.mod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional ModMenu integration: only loaded when ModMenu is installed. */
public class SkyAhModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SkyAhConfigScreen();
    }
}

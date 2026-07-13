package com.pmchat.client;

import com.pmchat.screen.PmSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Интеграция с Mod Menu: кнопка настроек открывает наш экран. */
public class PmModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PmSettingsScreen::new;
    }
}

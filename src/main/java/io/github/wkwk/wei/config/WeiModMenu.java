package io.github.wkwk.wei.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu の設定ボタンの入口。Mod Menu があるときだけ読み込まれる。 */
public final class WeiModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WeiConfigScreen::new;
    }
}

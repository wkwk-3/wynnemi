package io.github.wkwk.wei.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu の「設定」ボタンから WEI の設定画面を開く（Mod Menu が入っているときだけ読み込まれる） */
public final class WeiModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WeiConfigScreen::new;
    }
}

package io.github.wkwk.wynnemi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/wynnemi.json。変更後は /wynnemi reload で反映される。 */
public final class WynnEmiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile WynnEmiConfig instance = new WynnEmiConfig();

    /** Wynncraft 接続中は EMI からバニラのアイテムとレシピを隠す */
    public boolean hideVanillaOnWynncraft = true;
    /** 「入手元」アイテム（例: Dungeon: Decrepit Sewers）をアイテム一覧にも並べる。検索から逆引きできる */
    public boolean showSourcesInIndex = true;
    /** 入手方法のレシピを登録する */
    public boolean showObtainRecipes = true;
    /** 素材→使える製作職業、採集職業→素材 のレシピを登録する */
    public boolean showProfessionRecipes = true;
    /** 必要クエスト・未鑑定ボックスの中身・セット装備のレシピを登録する */
    public boolean showGearRecipes = true;
    /** EMI のお気に入りに入れた Wynncraft のアイテムを、Wynntils のお気に入りにも入れる */
    public boolean syncFavoritesToWynntils = true;

    public static WynnEmiConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("wynnemi.json");
        WynnEmiConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, WynnEmiConfig.class);
            } catch (Exception e) {
                WynnEmiClient.LOGGER.warn("Failed to read {}, using defaults", path, e);
            }
        }
        if (loaded == null) loaded = new WynnEmiConfig();
        instance = loaded;

        // 足りない項目を補って書き戻す
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(loaded, writer);
        } catch (Exception e) {
            WynnEmiClient.LOGGER.warn("Failed to write {}", path, e);
        }
    }

    String signature() {
        // EMI の表示に関わる項目だけ（お気に入りの連動は表示に関係しない）
        return hideVanillaOnWynncraft + "," + showSourcesInIndex + "," + showObtainRecipes + "," + showProfessionRecipes
                + "," + showGearRecipes;
    }

    public static String currentSignature() {
        return instance.signature();
    }
}

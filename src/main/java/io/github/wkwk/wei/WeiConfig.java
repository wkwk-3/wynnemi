package io.github.wkwk.wei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/wei.json。手で編集したら /wei reload。 */
public final class WeiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile WeiConfig instance = new WeiConfig();

    /** Wynncraft 接続中はバニラのアイテムとレシピを隠す */
    public boolean hideVanillaOnWynncraft = true;
    /** 入手元の仮想アイテム（例: Dungeon: Decrepit Sewers）を一覧に並べる */
    public boolean showSourcesInIndex = true;
    /** 入手方法のレシピ */
    public boolean showObtainRecipes = true;
    /** 素材 → 製作職業、採集職業 → 素材 のレシピ */
    public boolean showProfessionRecipes = true;
    /** 必要クエスト・未鑑定ボックス・セット装備・アスペクトのティアのレシピ */
    public boolean showGearRecipes = true;
    /** EMI のお気に入りを Wynntils のお気に入りにも入れる */
    public boolean syncFavoritesToWynntils = true;
    /** 報酬チェストの高額アイテムを強調する（要 Wynnventory） */
    public boolean lootHighlightEnabled = true;
    public boolean lootHighlightSound = true;
    /** 既定の基準（LE。1 LE = 4096 エメラルド） */
    public double lootHighlightThresholdLe = 1.5;
    /**
     * 種類・レア度別の基準。最も細かい一致（種類+レア度 > 種類 > レア度）を使い、
     * 一致が無ければ lootHighlightThresholdLe。負は通知しない。
     */
    public java.util.List<LootRule> lootRules = new java.util.ArrayList<>();

    public static final class LootRule {
        /** gearBox, gear, ingredient, ... または any */
        public String type = "any";
        /** normal, unique, ..., mythic / tier0〜tier6 または any */
        public String rarity = "any";
        public double thresholdLe = 1.5;

        public LootRule() {}

        public LootRule(String type, String rarity, double thresholdLe) {
            this.type = type;
            this.rarity = rarity;
            this.thresholdLe = thresholdLe;
        }
    }

    public double thresholdFor(String type, String rarity) {
        double best = lootHighlightThresholdLe;
        int bestScore = -1;
        if (lootRules != null) {
            for (LootRule r : lootRules) {
                boolean typeAny = "any".equalsIgnoreCase(r.type);
                boolean rarityAny = "any".equalsIgnoreCase(r.rarity);
                if (!typeAny && !r.type.equalsIgnoreCase(type)) continue;
                if (!rarityAny && !r.rarity.equalsIgnoreCase(rarity)) continue;
                int score = (typeAny ? 0 : 2) + (rarityAny ? 0 : 1);
                if (score >= bestScore) {
                    bestScore = score;
                    best = r.thresholdLe;
                }
            }
        }
        return best;
    }

    /** lowest / average / average80（上下 10% を除く）/ median / highest */
    public String lootHighlightPrice = "lowest";
    /** 強調色（ARGB） */
    public int lootHighlightColor = 0xFF00FF66;
    /**
     * 調査用ログの上書き。null ならプレリリースのみ。
     * 旧版の {@code diagnostics} は既定値 true が保存されているため、別名にして無視する
     * （読み込み時に捨てられ、書き戻しで消える）。
     */
    public Boolean diagnosticsOverride = null;

    public static WeiConfig get() {
        return instance;
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("wei.json");
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            Wei.LOGGER.warn("Failed to write {}", path, e);
        }
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("wei.json");
        WeiConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = fromJson(reader);
            } catch (Exception e) {
                Wei.LOGGER.warn("Failed to read {}, using defaults", path, e);
            }
        }
        if (loaded == null) loaded = new WeiConfig();
        instance = loaded;

        // 欠けた項目を補って書き戻す
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(loaded, writer);
        } catch (Exception e) {
            Wei.LOGGER.warn("Failed to write {}", path, e);
        }
    }

    /** 読み込みと旧形式の移行。空なら既定値（ファイルの読み書きはしない） */
    static WeiConfig fromJson(Reader reader) {
        WeiConfig loaded = GSON.fromJson(reader, WeiConfig.class);
        if (loaded == null) loaded = new WeiConfig();
        if (loaded.lootRules == null) loaded.lootRules = new java.util.ArrayList<>();
        // 手で書き換えた設定の欠けを補う（null のままだと判定で落ちる）
        loaded.lootRules.removeIf(java.util.Objects::isNull);
        for (LootRule r : loaded.lootRules) {
            if (r.type == null) r.type = "any";
            if (r.rarity == null) r.rarity = "any";
        }
        if (loaded.lootHighlightPrice == null) loaded.lootHighlightPrice = "lowest";
        // 旧初期値（橙）を現初期値（緑）に移行。3.0.0 で削除予定
        if (loaded.lootHighlightColor == 0xA0FFAA00) loaded.lootHighlightColor = 0xFF00FF66;
        return loaded;
    }

    String signature() {
        // EMI の表示に関わる項目のみ
        return hideVanillaOnWynncraft + "," + showSourcesInIndex + "," + showObtainRecipes + "," + showProfessionRecipes
                + "," + showGearRecipes;
    }

    public static String currentSignature() {
        return instance.signature();
    }
}

package io.github.wkwk.wei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/wei.json。変更後は /wei reload で反映される。 */
public final class WeiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile WeiConfig instance = new WeiConfig();

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
    /** ルートチェスト・報酬チェストで、取引所の価格が高いアイテムのスロットに色を付ける（Wynnventory が必要） */
    public boolean lootHighlightEnabled = true;
    /** 高額アイテムがあったら音を鳴らす */
    public boolean lootHighlightSound = true;
    /** 高額とみなす価格（LE。1 LE = 4096 エメラルド） */
    public double lootHighlightThresholdLe = 1.5;
    /**
     * 種類とレア度ごとの基準。当てはまる中で一番細かい指定（種類+レア度 > 種類だけ > レア度だけ）を使い、
     * どれにも当てはまらなければ lootHighlightThresholdLe。基準が負ならその組み合わせは通知しない。
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

    /** その種類・レア度の基準（LE）。負なら通知しない */
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
    /** 判定に使う価格: lowest（最安値）/ average / average80（上下10%を除いた平均）/ median / highest */
    public String lootHighlightPrice = "lowest";
    /** スロットの色（ARGB） */
    public int lootHighlightColor = 0xFF00FF66;
    /** 調査用ログ。null ならプレリリースだけ出す */
    public Boolean diagnostics = null;

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
                loaded = GSON.fromJson(reader, WeiConfig.class);
            } catch (Exception e) {
                Wei.LOGGER.warn("Failed to read {}, using defaults", path, e);
            }
        }
        if (loaded == null) loaded = new WeiConfig();
        if (loaded.lootRules == null) loaded.lootRules = new java.util.ArrayList<>();
        // 1.0.1-test.14 までの初期値（橙）は Wynntils のレア度の色と紛らわしいので、新しい初期値（緑）にする
        if (loaded.lootHighlightColor == 0xA0FFAA00) loaded.lootHighlightColor = 0xFF00FF66;
        instance = loaded;

        // 足りない項目を補って書き戻す
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(loaded, writer);
        } catch (Exception e) {
            Wei.LOGGER.warn("Failed to write {}", path, e);
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

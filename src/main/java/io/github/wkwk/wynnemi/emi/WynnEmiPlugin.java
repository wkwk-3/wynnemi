package io.github.wkwk.wynnemi.emi;

import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.wynnitem.type.ItemObtainInfo;
import com.wynntils.models.wynnitem.type.ItemObtainType;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import io.github.wkwk.wynnemi.WynnEmiClient;
import io.github.wkwk.wynnemi.WynnEmiConfig;
import io.github.wkwk.wynnemi.data.WynnCatalog;
import io.github.wkwk.wynnemi.data.WynnDataWatcher;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

@EmiEntrypoint
public final class WynnEmiPlugin implements EmiPlugin {
    @Override
    public void initialize(EmiInitRegistry registry) {
        // お気に入り保存用（PF-EMI では EmiRegistry 側の登録は非推奨）
        registry.addIngredientSerializer(WynnEmiStack.class, new WynnEmiStackSerializer());
    }

    @Override
    public void register(EmiRegistry registry) {
        WynnDataWatcher.markEmiRegistered();

        WynnEmiConfig config = WynnEmiConfig.get();
        WynnCatalog catalog = WynnCatalog.current();

        WynnEmiStackSerializer.clear();

        // 4. Wynncraft にいる間はバニラのアイテム・レシピを隠す（無効化は全プラグインの後に適用される）
        if (config.hideVanillaOnWynncraft && catalog.onWynncraft()) {
            registry.removeEmiStacks(stack -> !(stack instanceof WynnEmiStack));
            registry.removeRecipes(recipe -> !(recipe instanceof WynnLinkRecipe));
        }

        if (catalog.entries().isEmpty()) {
            // Wynntils のデータがまだ無い。揃ったら WynnDataWatcher が EMI を再読み込みする
            return;
        }

        WynnSources sources = new WynnSources();
        Set<Identifier> usedRecipeIds = new HashSet<>();

        // 5. 入手方法の種類ごとのカテゴリ
        Map<ItemObtainType, EmiRecipeCategory> obtainCategories = new EnumMap<>(ItemObtainType.class);
        if (config.showObtainRecipes) {
            for (ItemObtainType type : ItemObtainType.values()) {
                WynnEmiStack icon = sources.typeStack(type);
                EmiRecipeCategory category = new EmiRecipeCategory(
                        WynnEmiClient.id("obtain/" + type.name().toLowerCase(Locale.ROOT)), icon);
                obtainCategories.put(type, category);
                registry.addCategory(category);
                // 作業台欄のアイコンを押すと、その種類で手に入る全アイテムが出る
                registry.addWorkstation(category, icon);
            }
        }

        EmiRecipeCategory craftingCategory = new EmiRecipeCategory(
                WynnEmiClient.id("crafting_use"), EmiStack.of(Items.CRAFTING_TABLE));
        EmiRecipeCategory gatheringCategory = new EmiRecipeCategory(
                WynnEmiClient.id("gathering"), EmiStack.of(Items.IRON_PICKAXE));
        if (config.showProfessionRecipes) {
            registry.addCategory(craftingCategory);
            registry.addCategory(gatheringCategory);
        }

        for (WynnCatalog.Entry entry : catalog.entries()) {
            // 1〜3. アイテム一覧への登録（説明文つき・個別に区別される）
            WynnEmiStack item = WynnEmiStack.ofItem(entry.key(), entry.stack(), entry.displayName());
            registry.addEmiStack(item);
            WynnEmiStackSerializer.remember(item);
            // 色付き・アイコン付きの名前でも普通の名前で検索できるように
            registry.addAlias(item, Component.literal(entry.plainName()));

            // 5. 入手元 → アイテム
            if (config.showObtainRecipes) {
                for (ItemObtainInfo info : entry.obtainInfo()) {
                    EmiRecipeCategory category = obtainCategories.get(info.sourceType());
                    if (category == null) continue;
                    WynnEmiStack source = sources.sourceStack(info);
                    Identifier id = uniqueId(usedRecipeIds,
                            "/obtain/" + info.sourceType().name().toLowerCase(Locale.ROOT) + "/" + item.getId().getPath()
                                    + "/" + Integer.toHexString(source.wynnKey().hashCode()));
                    if (id == null) continue;
                    registry.addRecipe(new WynnLinkRecipe(category, id, source, item,
                            info.name().map(Component::literal).orElse(null)));
                }
            }

            if (config.showProfessionRecipes) {
                // 素材 → 使える製作職業（U で使い道、職業で R すると使える素材一覧）
                for (ProfessionType profession : entry.craftProfessions()) {
                    Identifier id = uniqueId(usedRecipeIds,
                            "/crafting_use/" + item.getId().getPath() + "/" + profession.name().toLowerCase(Locale.ROOT));
                    if (id == null) continue;
                    registry.addRecipe(new WynnLinkRecipe(craftingCategory, id, item,
                            sources.professionStack(profession), null));
                }
                // 採集職業 → 素材
                if (entry.gatherProfession() != null) {
                    Identifier id = uniqueId(usedRecipeIds, "/gathering/" + item.getId().getPath());
                    if (id != null) {
                        registry.addRecipe(new WynnLinkRecipe(gatheringCategory, id,
                                sources.professionStack(entry.gatherProfession()), item, null));
                    }
                }
            }
        }

        if (config.showProfessionRecipes) {
            for (WynnEmiStack profession : sources.allProfessionStacks()) {
                WynnEmiStackSerializer.remember(profession);
                registry.addWorkstation(craftingCategory, profession);
            }
        }

        for (WynnEmiStack source : sources.allSourceStacks()) {
            WynnEmiStackSerializer.remember(source);
            if (config.showSourcesInIndex) {
                // 一覧から「Decrepit」などで検索して U → そこで手に入るアイテムを逆引き
                registry.addEmiStack(source);
            }
        }
        if (config.showSourcesInIndex) {
            for (WynnEmiStack profession : sources.allProfessionStacks()) {
                registry.addEmiStack(profession);
            }
        }
    }

    private static Identifier uniqueId(Set<Identifier> used, String path) {
        Identifier id = WynnEmiClient.id(path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
        return used.add(id) ? id : null;
    }
}

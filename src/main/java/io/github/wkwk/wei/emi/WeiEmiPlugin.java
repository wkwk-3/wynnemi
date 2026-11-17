package io.github.wkwk.wei.emi;

import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.wynnitem.type.ItemObtainType;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.WeiConfig;
import io.github.wkwk.wei.data.WynnCatalog;
import io.github.wkwk.wei.data.WynnDataWatcher;
import io.github.wkwk.wei.util.Names;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@EmiEntrypoint
public final class WeiEmiPlugin implements EmiPlugin {
    /** レベル順に並べる（WynnLinkRecipe の sortKey） */
    private static final Comparator<EmiRecipe> BY_SORT_KEY = Comparator.comparingInt(
            r -> r instanceof WynnLinkRecipe link ? link.sortKey() : 0);

    @Override
    public void initialize(EmiInitRegistry registry) {
        // お気に入り保存用（PF-EMI では EmiRegistry 側の登録は非推奨）
        registry.addIngredientSerializer(WynnEmiStack.class, new WynnEmiStackSerializer());
    }

    @Override
    public void register(EmiRegistry registry) {
        // このスレッドでは Wynntils に触れず、クライアントスレッドで作ったスナップショットだけを使う
        WeiConfig config = WeiConfig.get();
        WynnCatalog catalog = WynnCatalog.current();
        String view = WynnDataWatcher.viewSignature();
        boolean onWynncraft = WynnDataWatcher.onWynncraft();
        WynnDataWatcher.markEmiRegistered(catalog, view);

        WynnEmiStackSerializer.clear();

        // 1. 手持ちのアイテムでも R/U が効くように（該当しないスロットは EMI 標準の処理へ）
        registry.addGenericStackProvider(new WynnSlotStackProvider());

        // Wynncraft にいる間はバニラのアイテム・レシピを隠す（無効化は全プラグインの後に適用される）
        if (config.hideVanillaOnWynncraft && onWynncraft) {
            registry.removeEmiStacks(stack -> !(stack instanceof WynnEmiStack));
            registry.removeRecipes(recipe -> !(recipe instanceof WynnLinkRecipe) && !(recipe instanceof WynnSetRecipe));
        }

        if (catalog.entries().isEmpty()) {
            // Wynntils のデータがまだ無い。揃ったら WynnDataWatcher が EMI を再読み込みする
            WynnLookup.publish(new WynnLookup(Map.of(), Map.of(), Map.of(), Map.of()));
            return;
        }

        WynnSources sources = new WynnSources();
        Set<Identifier> usedRecipeIds = new HashSet<>();

        // ---- カテゴリ
        Map<ItemObtainType, EmiRecipeCategory> obtainCategories = new EnumMap<>(ItemObtainType.class);
        if (config.showObtainRecipes) {
            for (ItemObtainType type : ItemObtainType.values()) {
                WynnEmiStack icon = sources.typeStack(type);
                EmiRecipeCategory category = new EmiRecipeCategory(
                        Wei.id("obtain/" + type.name().toLowerCase(Locale.ROOT)), icon);
                obtainCategories.put(type, category);
                registry.addCategory(category);
                // 作業台欄のアイコンを押すと、その種類で手に入る全アイテムが出る
                registry.addWorkstation(category, icon);
            }
        }

        // 4. 製作素材は必要レベル順
        EmiStack craftingIcon = EmiStack.of(Items.CRAFTING_TABLE);
        EmiRecipeCategory craftingCategory = new EmiRecipeCategory(
                Wei.id("crafting_use"), craftingIcon, craftingIcon, BY_SORT_KEY);
        EmiStack gatheringIcon = EmiStack.of(Items.IRON_PICKAXE);
        EmiRecipeCategory gatheringCategory = new EmiRecipeCategory(
                Wei.id("gathering"), gatheringIcon, gatheringIcon, BY_SORT_KEY);
        if (config.showProfessionRecipes) {
            registry.addCategory(craftingCategory);
            registry.addCategory(gatheringCategory);
        }

        // 5. 必要クエスト
        EmiStack questIcon = EmiStack.of(Items.WRITABLE_BOOK);
        EmiRecipeCategory questCategory = new EmiRecipeCategory(
                Wei.id("quest_required"), questIcon, questIcon, BY_SORT_KEY);
        // 2. 未鑑定ボックスの中身（レベル順）
        EmiStack boxIcon = EmiStack.of(Items.CHEST);
        EmiRecipeCategory boxCategory = new EmiRecipeCategory(
                Wei.id("gear_box"), boxIcon, boxIcon, BY_SORT_KEY);
        // 6. セット装備
        EmiStack setIcon = EmiStack.of(Items.ARMOR_STAND);
        EmiRecipeCategory setCategory = new EmiRecipeCategory(Wei.id("set"), setIcon);
        if (config.showGearRecipes) {
            registry.addCategory(questCategory);
            registry.addCategory(boxCategory);
            registry.addCategory(setCategory);
        }

        // ---- アイテム
        Map<String, WynnEmiStack> byKey = new HashMap<>();
        Map<String, String> keyByPlainName = new HashMap<>();
        Map<String, String> favoriteNameByKey = new HashMap<>();
        Map<String, WynnEmiStack> boxStacks = new HashMap<>();
        Map<String, List<WynnLookup.BoxCandidate>> boxCandidates = new HashMap<>();

        for (WynnCatalog.Entry entry : catalog.entries()) {
            // アイテム一覧への登録（説明文つき・個別に区別される）
            WynnEmiStack item = WynnEmiStack.ofItem(entry.key(), entry.stack(), entry.displayName(), entry.tooltip());
            registry.addEmiStack(item);
            WynnEmiStackSerializer.remember(item);
            byKey.put(entry.key(), item);
            keyByPlainName.putIfAbsent(entry.plainName().toLowerCase(Locale.ROOT), entry.key());
            if (entry.favoriteName() != null) favoriteNameByKey.put(entry.key(), entry.favoriteName());

            // 3. 色付き・アイコン付きの名前でも、普通の名前とレアリティ・種類・レベルなどで検索できるように
            registry.addAlias(item, Component.literal(entry.aliases() == null
                    ? entry.plainName()
                    : entry.plainName() + " " + entry.aliases()));

            // 入手元 → アイテム
            if (config.showObtainRecipes) {
                for (WynnCatalog.ObtainSnapshot info : entry.obtain()) {
                    EmiRecipeCategory category = obtainCategories.get(info.type());
                    if (category == null) continue;
                    WynnEmiStack source = sources.sourceStack(info);
                    Identifier id = uniqueId(usedRecipeIds,
                            "/obtain/" + info.type().name().toLowerCase(Locale.ROOT) + "/" + item.getId().getPath()
                                    + "/" + Integer.toHexString(source.wynnKey().hashCode()));
                    if (id == null) continue;
                    registry.addRecipe(new WynnLinkRecipe(category, id, source, item,
                            info.caption() == null ? null : Component.literal(info.caption())));
                }
            }

            if (config.showProfessionRecipes) {
                // 4. 素材 → 使える製作職業（U で使い道、職業で R すると使える素材一覧。レベル順）
                for (ProfessionType profession : entry.craftProfessions()) {
                    Identifier id = uniqueId(usedRecipeIds,
                            "/crafting_use/" + item.getId().getPath() + "/" + profession.name().toLowerCase(Locale.ROOT));
                    if (id == null) continue;
                    registry.addRecipe(new WynnLinkRecipe(craftingCategory, id, item,
                            sources.professionStack(profession), levelCaption(entry.level()), false, entry.level()));
                }
                // 採集職業 → 素材
                if (entry.gatherProfession() != null) {
                    Identifier id = uniqueId(usedRecipeIds, "/gathering/" + item.getId().getPath());
                    if (id != null) {
                        registry.addRecipe(new WynnLinkRecipe(gatheringCategory, id,
                                sources.professionStack(entry.gatherProfession()), item,
                                levelCaption(entry.level()), false, entry.level()));
                    }
                }
            }

            if (config.showGearRecipes) {
                // 5. クエスト → 使えるようになる装備（装備で R、クエストで U）
                if (entry.requiredQuest() != null) {
                    WynnEmiStack quest = sources.sourceStack(new WynnCatalog.ObtainSnapshot(
                            ItemObtainType.QUEST, entry.requiredQuest(), entry.requiredQuest()));
                    Identifier id = uniqueId(usedRecipeIds, "/quest_required/" + item.getId().getPath());
                    if (id != null) {
                        registry.addRecipe(new WynnLinkRecipe(questCategory, id, quest, item,
                                Component.translatable("wei.recipe.requires_quest", entry.requiredQuest()),
                                false, entry.level()));
                    }
                }

                // 2. 未鑑定ボックス → 候補（ボックスで R、装備で U。見た目は ボックス → 装備）
                if (entry.boxFamily() != null) {
                    WynnCatalog.BoxFamily family = entry.boxFamily();
                    WynnEmiStack box = boxStacks.computeIfAbsent(family.key(), k -> WynnEmiStack.ofVirtual(
                            k, new ItemStack(Items.CHEST),
                            family.displayName().copy().withStyle(ChatFormatting.GRAY),
                            List.of(Component.translatable("wei.tooltip.box").withStyle(ChatFormatting.DARK_GRAY))));
                    Identifier id = uniqueId(usedRecipeIds, "/gear_box/" + item.getId().getPath());
                    if (id != null) {
                        WynnLinkRecipe recipe = new WynnLinkRecipe(boxCategory, id, box, item,
                                levelCaption(entry.level()), true, entry.level());
                        registry.addRecipe(recipe);
                        boxCandidates.computeIfAbsent(family.key(), k -> new ArrayList<>())
                                .add(new WynnLookup.BoxCandidate(entry.level(), recipe));
                    }
                }
            }
        }

        // 6. セット装備
        if (config.showGearRecipes) {
            for (WynnCatalog.SetSnapshot set : catalog.sets()) {
                List<EmiStack> members = new ArrayList<>();
                for (String key : set.memberKeys()) {
                    WynnEmiStack member = byKey.get(key);
                    if (member != null) members.add(member);
                }
                if (members.isEmpty()) continue;
                WynnEmiStack setStack = WynnEmiStack.ofVirtual(
                        "set/" + set.name(),
                        members.get(0).getItemStack(),
                        set.bonusTooltip().get(0),
                        set.bonusTooltip().subList(1, set.bonusTooltip().size()));
                WynnEmiStackSerializer.remember(setStack);
                Identifier id = uniqueId(usedRecipeIds, "/set/" + Names.idPath(set.name()));
                if (id != null) {
                    registry.addRecipe(new WynnSetRecipe(setCategory, id, setStack, members));
                }
            }
        }

        // ---- 仮想アイテム（入手元・職業・ボックス）
        if (config.showProfessionRecipes) {
            for (WynnEmiStack profession : sources.allProfessionStacks()) {
                registry.addWorkstation(craftingCategory, profession);
            }
        }
        for (WynnEmiStack source : sources.allSourceStacks()) {
            WynnEmiStackSerializer.remember(source);
            // 一覧から「Decrepit」などで検索して U → そこで手に入るアイテムを逆引き
            if (config.showSourcesInIndex) registry.addEmiStack(source);
        }
        for (WynnEmiStack profession : sources.allProfessionStacks()) {
            WynnEmiStackSerializer.remember(profession);
            if (config.showSourcesInIndex) registry.addEmiStack(profession);
        }
        Map<String, WynnLookup.BoxIndex> boxes = new HashMap<>();
        for (Map.Entry<String, WynnEmiStack> e : boxStacks.entrySet()) {
            WynnEmiStackSerializer.remember(e.getValue());
            if (config.showSourcesInIndex) registry.addEmiStack(e.getValue());
            List<WynnLookup.BoxCandidate> candidates = new ArrayList<>(boxCandidates.getOrDefault(e.getKey(), List.of()));
            candidates.sort(Comparator.comparingInt(WynnLookup.BoxCandidate::level));
            boxes.put(e.getKey(), new WynnLookup.BoxIndex(e.getValue(), List.copyOf(candidates)));
        }

        WynnLookup.publish(new WynnLookup(Map.copyOf(byKey), Map.copyOf(keyByPlainName),
                Map.copyOf(favoriteNameByKey), Map.copyOf(boxes)));
    }

    private static Component levelCaption(int level) {
        return level > 0 ? Component.translatable("wei.recipe.level", level) : null;
    }

    private static Identifier uniqueId(Set<Identifier> used, String path) {
        Identifier id = Wei.id(Names.idPath(path));
        return used.add(id) ? id : null;
    }
}

package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.Diag;
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
    /** WynnLinkRecipe の sortKey 順 */
    private static final Comparator<EmiRecipe> BY_SORT_KEY = Comparator.comparingInt(
            r -> r instanceof WynnLinkRecipe link ? link.sortKey() : 0);

    @Override
    public void initialize(EmiInitRegistry registry) {
        // お気に入りの保存形式（EmiRegistry 側の登録は非推奨）
        registry.addIngredientSerializer(WynnEmiStack.class, new WynnEmiStackSerializer());
    }

    @Override
    public void register(EmiRegistry registry) {
        // EMI のスレッド。Wynntils には触れずスナップショットだけを使う
        WeiConfig config = WeiConfig.get();
        WynnCatalog catalog = WynnCatalog.current();
        String view = WynnDataWatcher.viewSignature();
        boolean onWynncraft = WynnDataWatcher.onWynncraft();
        WynnDataWatcher.markEmiRegistered(catalog, view);
        long diagStart = System.nanoTime();
        // diag#3: 記録済みの接続状態と実際の値のずれは二重読み込みの原因
        Diag.log(3, "Plugin register START: cachedOnWynncraft={} liveOnWynncraft={} catalog={} items, sets={}",
                onWynncraft, WynnCatalog.isOnWynncraftNow(), catalog.entries().size(), catalog.sets().size());

        WynnEmiStackSerializer.clear();

        // 手持ちのアイテムの R/U
        registry.addGenericStackProvider(new WynnSlotStackProvider());
        WynnSlotStackProvider.registerHighlight();
        WynnBoxInstances.registerOnce();

        // Wynncraft ではバニラを隠す（無効化は全プラグインの後に適用される）
        if (config.hideVanillaOnWynncraft && onWynncraft) {
            registry.removeEmiStacks(stack -> !(stack instanceof WynnEmiStack));
            registry.removeRecipes(recipe -> !(recipe instanceof WynnLinkRecipe) && !(recipe instanceof WynnSetRecipe)
                    && !(recipe instanceof WynnAspectRecipe));
        }

        if (catalog.entries().isEmpty()) {
            // データ未着。揃えば WynnDataWatcher が再読み込みする
            Diag.warn(1, "Plugin registered with an EMPTY catalog: WEI favorites/hidden stacks in EMI's file will be dropped on this load");
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
                // 作業台欄のアイコンでその種類の全アイテムを引ける
                registry.addWorkstation(category, icon);
            }
        }

        // 製作素材は必要レベル順
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

        EmiStack questIcon = EmiStack.of(Items.WRITABLE_BOOK);
        EmiRecipeCategory questCategory = new EmiRecipeCategory(
                Wei.id("quest_required"), questIcon, questIcon, BY_SORT_KEY);
        EmiStack boxIcon = EmiStack.of(Items.CHEST);
        EmiRecipeCategory boxCategory = new EmiRecipeCategory(
                Wei.id("gear_box"), boxIcon, boxIcon, BY_SORT_KEY);
        // セット装備
        EmiStack setIcon = EmiStack.of(Items.ARMOR_STAND);
        EmiRecipeCategory setCategory = new EmiRecipeCategory(Wei.id("set"), setIcon);
        EmiStack aspectIcon = EmiStack.of(Items.NETHER_STAR);
        EmiRecipeCategory aspectCategory = new EmiRecipeCategory(Wei.id("aspect_tier"), aspectIcon);
        if (config.showGearRecipes) {
            registry.addCategory(aspectCategory);
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
            WynnEmiStack item = WynnEmiStack.ofItem(entry.key(), entry.stack(), entry.displayName(), entry.tooltip());
            registry.addEmiStack(item);
            WynnEmiStackSerializer.remember(item);
            byKey.put(entry.key(), item);
            keyByPlainName.putIfAbsent(entry.plainName().toLowerCase(Locale.ROOT), entry.key());
            if (entry.favoriteName() != null) favoriteNameByKey.put(entry.key(), entry.favoriteName());

            // 素の名前・レアリティ・種類・レベルでも検索できるよう別名を付ける
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
                // 素材 → 製作職業
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
                // クエスト → 装備
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

                // 未鑑定ボックス → 候補（ボックスで R、装備で U）
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

        // セット装備
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

        // アスペクトのティア
        if (config.showGearRecipes) {
            for (WynnCatalog.AspectSnapshot aspect : catalog.aspects()) {
                WynnEmiStack stack = byKey.get(aspect.key());
                if (stack == null || aspect.tiers().isEmpty()) continue;
                Identifier id = uniqueId(usedRecipeIds, "/aspect/" + Names.idPath(aspect.key()));
                if (id != null) registry.addRecipe(new WynnAspectRecipe(aspectCategory, id, stack, aspect.tiers()));
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
            // 入手元を検索して U で逆引きできる
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
        Diag.log(3, "Plugin register DONE in {}ms: {} stacks, {} recipe ids, {} sources, {} box families, hideVanilla={}",
                Diag.ms(diagStart), byKey.size(), usedRecipeIds.size(), sources.sourceCount(), boxes.size(),
                config.hideVanillaOnWynncraft && onWynncraft);
    }

    private static Component levelCaption(int level) {
        return level > 0 ? Component.translatable("wei.recipe.level", level) : null;
    }

    private static Identifier uniqueId(Set<Identifier> used, String path) {
        Identifier id = Wei.id(Names.idPath(path));
        return used.add(id) ? id : null;
    }
}

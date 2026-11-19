package io.github.wkwk.wei.emi;

import com.wynntils.models.items.items.game.GearBoxItem;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import io.github.wkwk.wei.emihook.EmiHooks;
import io.github.wkwk.wei.util.Names;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 手持ちの未鑑定ボックス用の一時スタック。R/U でその箱の候補（Wynntils の判定）への
 * 「未鑑定ボックス」レシピだけを出す。
 * EMI には登録せず、{@link EmiHooks#registerRecipeOverride} でページを差し込む。
 */
final class WynnBoxInstances {
    private static final String PREFIX = io.github.wkwk.wei.util.StackKeys.BOX_INSTANCE_PREFIX;
    /** 一時スタックのキー → 表示するレシピ */
    private static final Map<String, List<EmiRecipe>> RECIPES = new ConcurrentHashMap<>();
    private static final Map<String, WynnEmiStack> STACKS = new ConcurrentHashMap<>();
    private static boolean registered;

    private WynnBoxInstances() {}

    static synchronized void registerOnce() {
        if (registered) return;
        registered = true;
        // その場限りのスタックなので履歴・お気に入りに残さない（次回起動時には読めない）
        EmiHooks.registerTransient(
                stack -> stack instanceof WynnEmiStack wynn && wynn.key().startsWith(PREFIX),
                io.github.wkwk.wei.util.StackKeys::isBoxInstanceJson);
        EmiHooks.registerRecipeOverride((EmiIngredient stack, boolean uses) -> {
            if (!(stack instanceof WynnEmiStack wynn) || !wynn.key().startsWith(PREFIX)) return null;
            List<EmiRecipe> recipes = RECIPES.get(wynn.key());
            if (recipes == null || recipes.isEmpty()) return null;
            Map<EmiRecipeCategory, List<EmiRecipe>> pages = new LinkedHashMap<>();
            for (EmiRecipe r : recipes) pages.computeIfAbsent(r.getCategory(), c -> new ArrayList<>()).add(r);
            io.github.wkwk.wei.Diag.log(7, "Box: show {} recipe(s) for '{}' ({})", recipes.size(), wynn.key(), uses ? "U" : "R");
            return pages;
        });
    }

    static @Nullable WynnEmiStack create(ItemStack boxStack, GearBoxItem box, WynnLookup.BoxIndex index,
            List<WynnEmiStack> candidates) {
        registerOnce();
        List<String> wanted = candidates.stream().map(WynnEmiStack::key).sorted().toList();
        // 種類・ティア・範囲が同じでも候補が違えば別のスタック
        String key = PREFIX + box.getGearType() + "/" + box.getGearTier() + "/" + box.getLevelRange().low() + "-"
                + box.getLevelRange().high() + "/" + String.join(",", wanted);
        WynnEmiStack existing = STACKS.get(key);
        if (existing != null) return existing;

        // 候補 → レシピの索引は系統ごとに 1 回だけ作る
        Map<String, List<EmiRecipe>> byGear = BY_GEAR.computeIfAbsent(index, WynnBoxInstances::indexByGear);
        List<EmiRecipe> recipes = new ArrayList<>();
        for (String gear : wanted) recipes.addAll(byGear.getOrDefault(gear, List.of()));
        io.github.wkwk.wei.Diag.log(7, "Box instance '{}' recipes={}", key, recipes.size());
        if (recipes.isEmpty()) return null;

        Component name = Component.literal(Names.strip(boxStack.getHoverName().getString()) + " "
                + box.getLevelRange().low() + "-" + box.getLevelRange().high());
        WynnEmiStack stack = WynnEmiStack.ofVirtual(key, boxStack.copy(), name,
                List.of(Component.translatable("wei.tooltip.boxInstance", candidates.size()).withStyle(ChatFormatting.DARK_GRAY)));
        RECIPES.put(key, List.copyOf(recipes));
        STACKS.put(key, stack);
        return stack;
    }

    private static final Map<WynnLookup.BoxIndex, Map<String, List<EmiRecipe>>> BY_GEAR =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private static Map<String, List<EmiRecipe>> indexByGear(WynnLookup.BoxIndex index) {
        Map<String, List<EmiRecipe>> out = new java.util.HashMap<>();
        for (WynnLookup.BoxCandidate c : index.candidates()) {
            java.util.Set<String> gears = new java.util.HashSet<>();
            for (EmiStack s : c.recipe().getOutputs()) if (s instanceof WynnEmiStack w) gears.add(w.key());
            for (EmiIngredient i : c.recipe().getInputs()) {
                for (EmiStack s : i.getEmiStacks()) if (s instanceof WynnEmiStack w) gears.add(w.key());
            }
            for (String g : gears) out.computeIfAbsent(g, k -> new ArrayList<>()).add(c.recipe());
        }
        return out;
    }

    /** EMI の再登録時に呼ぶ（古いレシピを参照しないため） */
    static void clear() {
        RECIPES.clear();
        STACKS.clear();
        BY_GEAR.clear();
    }
}

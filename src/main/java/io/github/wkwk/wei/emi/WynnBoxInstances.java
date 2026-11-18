package io.github.wkwk.wei.emi;

import com.wynntils.models.items.items.game.GearBoxItem;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.fork.EmiForkApi;
import io.github.wkwk.wei.Wei;
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
 * 手持ちの未鑑定ボックス1つぶんの一時的なスタック。R/U を押すと、
 * そのボックスの中身の候補（Wynntils の判定）への「未鑑定ボックス」のレシピだけを出す。
 * EMI には登録しない（EMI フォークの recipe override で中身を差し込む）。
 */
final class WynnBoxInstances {
    private static final String PREFIX = "boxinstance/";
    /** 一時スタックのキー → 出すレシピ。箱の種類・ティア・レベル範囲ごとに作り直さず使い回す */
    private static final Map<String, List<EmiRecipe>> RECIPES = new ConcurrentHashMap<>();
    private static final Map<String, WynnEmiStack> STACKS = new ConcurrentHashMap<>();
    private static boolean registered;

    private WynnBoxInstances() {}

    /** EMI フォークに「この一時スタックのページはこれ」と教える。1回だけ */
    static synchronized void registerOnce() {
        if (registered) return;
        registered = true;
        EmiForkApi.registerRecipeOverride((EmiIngredient stack, boolean uses) -> {
            if (!(stack instanceof WynnEmiStack wynn) || !wynn.key().startsWith(PREFIX)) return null;
            List<EmiRecipe> recipes = RECIPES.get(wynn.key());
            if (recipes == null || recipes.isEmpty()) return null;
            Map<EmiRecipeCategory, List<EmiRecipe>> pages = new LinkedHashMap<>();
            for (EmiRecipe r : recipes) pages.computeIfAbsent(r.getCategory(), c -> new ArrayList<>()).add(r);
            Wei.LOGGER.info("[WEI box] show {} recipe(s) for '{}' ({})", recipes.size(), wynn.key(), uses ? "U" : "R");
            return pages;
        });
    }

    static @Nullable WynnEmiStack create(ItemStack boxStack, GearBoxItem box, WynnLookup.BoxIndex index,
            List<WynnEmiStack> candidates) {
        registerOnce();
        String key = PREFIX + box.getGearType() + "/" + box.getGearTier() + "/" + box.getLevelRange().low() + "-"
                + box.getLevelRange().high() + "/" + candidates.size();
        WynnEmiStack existing = STACKS.get(key);
        if (existing != null) return existing;

        List<String> wanted = candidates.stream().map(WynnEmiStack::key).toList();
        List<EmiRecipe> recipes = new ArrayList<>();
        for (WynnLookup.BoxCandidate c : index.candidates()) {
            if (matches(c.recipe().getOutputs(), wanted) || matchesIngredients(c.recipe().getInputs(), wanted)) {
                recipes.add(c.recipe());
            }
        }
        Wei.LOGGER.info("[WEI box] instance '{}' candidates={} recipes={}", key, wanted, recipes.size());
        if (recipes.isEmpty()) return null;

        Component name = Component.literal(Names.strip(boxStack.getHoverName().getString()) + " "
                + box.getLevelRange().low() + "-" + box.getLevelRange().high());
        WynnEmiStack stack = WynnEmiStack.ofVirtual(key, boxStack.copy(), name,
                List.of(Component.translatable("wei.tooltip.boxInstance", candidates.size()).withStyle(ChatFormatting.DARK_GRAY)));
        RECIPES.put(key, List.copyOf(recipes));
        STACKS.put(key, stack);
        return stack;
    }

    private static boolean matches(List<EmiStack> stacks, List<String> wanted) {
        for (EmiStack s : stacks) if (s instanceof WynnEmiStack w && wanted.contains(w.key())) return true;
        return false;
    }

    private static boolean matchesIngredients(List<EmiIngredient> stacks, List<String> wanted) {
        for (EmiIngredient i : stacks) {
            for (EmiStack s : i.getEmiStacks()) if (s instanceof WynnEmiStack w && wanted.contains(w.key())) return true;
        }
        return false;
    }

    /** EMI の読み込み直しで古いレシピを指さないよう、作り直しのたびに空にする */
    static void clear() {
        RECIPES.clear();
        STACKS.clear();
    }
}

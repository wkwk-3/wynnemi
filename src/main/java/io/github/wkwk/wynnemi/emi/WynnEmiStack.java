package io.github.wkwk.wynnemi.emi;

import io.github.wkwk.wynnemi.WynnEmiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Wynncraft のアイテム（または「入手元」「職業」などの仮想アイテム）を表す EMI のスタック。
 *
 * <p>EMI 標準の {@code EmiStack.of(ItemStack)} はアイテムを素の ItemStack にコピーするため、
 * Wynntils が getTooltipLines() のオーバーライドで作っている説明文が消える。ここでは元の
 * ItemStack（Wynntils の Guide*ItemStack）をそのまま持ち、説明文もそこから取る。
 *
 * <p>また EMI は「アイテムの種類」で同一判定するため、見た目のベースが同じ（例: 全部ポーション）
 * Wynncraft のアイテムが1つにまとめられてしまう。キーを Wynncraft のアイテムごとに固有にして防ぐ。
 */
public final class WynnEmiStack extends EmiStack {
    private final String key;
    private final Identifier id;
    private final ItemStack stack;
    private final Component name;
    private final List<Component> extraTooltip;
    private final boolean useStackTooltip;

    private WynnEmiStack(String key, ItemStack stack, Component name, List<Component> extraTooltip, boolean useStackTooltip) {
        this.key = key;
        this.id = WynnEmiClient.id(toPath(key));
        this.stack = stack;
        this.name = name;
        this.extraTooltip = extraTooltip;
        this.useStackTooltip = useStackTooltip;
    }

    /** Wynntils のガイド用アイテム。説明文はアイテム自身が作る。 */
    public static WynnEmiStack ofItem(String key, ItemStack stack, Component name) {
        return new WynnEmiStack(key, stack, name, List.of(), true);
    }

    /** 入手元や職業などの仮想アイテム。説明文は name + extraTooltip。 */
    public static WynnEmiStack ofVirtual(String key, ItemStack icon, Component name, List<Component> extraTooltip) {
        return new WynnEmiStack(key, icon, name, List.copyOf(extraTooltip), false);
    }

    public String wynnKey() {
        return key;
    }

    @Override
    public EmiStack copy() {
        WynnEmiStack copy = new WynnEmiStack(key, stack, name, extraTooltip, useStackTooltip);
        copy.amount = this.amount;
        copy.chance = this.chance;
        copy.comparison = this.comparison;
        copy.setRemainder(getRemainder().copy());
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return amount == 0 || stack.isEmpty();
    }

    @Override
    public DataComponentPatch getComponentChanges() {
        return stack.getComponentsPatch();
    }

    @Override
    public Object getKey() {
        return "wynnemi:" + key;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    /** 元の ItemStack を返す（コピーすると Wynntils のサブクラスが失われる）。 */
    @Override
    public ItemStack getItemStack() {
        return stack;
    }

    @Override
    public List<Component> getTooltipText() {
        if (!useStackTooltip) {
            List<Component> lines = new ArrayList<>(1 + extraTooltip.size());
            lines.add(name);
            lines.addAll(extraTooltip);
            return lines;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            return stack.getTooltipLines(
                    Item.TooltipContext.EMPTY, mc.isSameThread() ? mc.player : null, TooltipFlag.NORMAL);
        } catch (Throwable t) {
            return List.of(name);
        }
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> list = new ArrayList<>();
        for (Component line : getTooltipText()) {
            list.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        list.addAll(super.getTooltip());
        return list;
    }

    @Override
    public Component getName() {
        return name;
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
        if ((flags & RENDER_ICON) != 0) {
            draw.renderItem(stack, x, y);
        }
    }

    /** Identifier に使えない文字を置き換える。重複はキー側で防いでいるので、衝突回避にハッシュを足す。 */
    private static String toPath(String key) {
        String cleaned = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return cleaned + "_" + Integer.toHexString(key.hashCode());
    }
}

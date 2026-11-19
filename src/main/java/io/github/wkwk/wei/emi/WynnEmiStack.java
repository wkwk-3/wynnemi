package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.Wei;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import dev.emi.emi.api.stack.EmiStack;
import io.github.wkwk.wei.util.Names;

/**
 * Wynncraft のアイテム、または入手元・職業などの仮想アイテムのスタック。
 *
 * <p>{@code EmiStack.of(ItemStack)} は素の ItemStack にコピーし、Wynntils が getTooltipLines() の
 * オーバーライドで作る説明文が消える。ここでは元の Guide*ItemStack をそのまま持つ。
 *
 * <p>EMI はアイテムの種類で同一判定するため、ベースが同じ（例: ポーション）アイテムがまとまる。
 * キーをアイテムごとに固有にして防ぐ。
 */
public final class WynnEmiStack extends EmiStack {
    private final String key;
    private final Identifier id;
    private final ItemStack stack;
    private final Component name;
    /** 構築時に取った説明文。EMI は別スレッドの索引作成でも読むため Wynntils には問い合わせない */
    private final List<Component> tooltip;

    private WynnEmiStack(String key, ItemStack stack, Component name, List<Component> tooltip) {
        this.key = key;
        this.id = Wei.id(toPath(key));
        this.stack = stack;
        this.name = name;
        this.tooltip = tooltip;
    }

    public static WynnEmiStack ofItem(String key, ItemStack stack, Component name, List<Component> tooltip) {
        return new WynnEmiStack(key, stack, name, List.copyOf(tooltip));
    }

    /** 仮想アイテム。説明文は name + extraTooltip。 */
    public static WynnEmiStack ofVirtual(String key, ItemStack icon, Component name, List<Component> extraTooltip) {
        List<Component> lines = new ArrayList<>(1 + extraTooltip.size());
        lines.add(name);
        lines.addAll(extraTooltip);
        return new WynnEmiStack(key, icon, name, List.copyOf(lines));
    }

    public String wynnKey() {
        return key;
    }

    @Override
    public EmiStack copy() {
        WynnEmiStack copy = new WynnEmiStack(key, stack, name, tooltip);
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

    public String key() {
        return key;
    }

    @Override
    public Object getKey() {
        return "wei:" + key;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    /** コピーしない（Wynntils のサブクラスが失われる） */
    @Override
    public ItemStack getItemStack() {
        return stack;
    }

    @Override
    public List<Component> getTooltipText() {
        return tooltip;
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

    /** 文字の置き換えで衝突しないよう元のキーのハッシュを足す */
    private static String toPath(String key) {
        return Names.idPath(key) + "_" + Integer.toHexString(key.hashCode());
    }
}

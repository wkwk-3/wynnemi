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
    /** 不変の説明文。EMI は別スレッドの検索インデックス作成でも呼ぶので、Wynntils には問い合わせない */
    private final List<Component> tooltip;

    private WynnEmiStack(String key, ItemStack stack, Component name, List<Component> tooltip) {
        this.key = key;
        this.id = Wei.id(toPath(key));
        this.stack = stack;
        this.name = name;
        this.tooltip = tooltip;
    }

    /** Wynntils のガイド用アイテム。説明文はカタログ構築時に取ったもの。 */
    public static WynnEmiStack ofItem(String key, ItemStack stack, Component name, List<Component> tooltip) {
        return new WynnEmiStack(key, stack, name, List.copyOf(tooltip));
    }

    /** 入手元や職業などの仮想アイテム。説明文は name + extraTooltip。 */
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

    @Override
    public Object getKey() {
        return "wei:" + key;
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

    /** 説明文の作り直しをホバー時に試したキー（ログを1回だけにする） */
    private static final java.util.Set<String> RETRIED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public List<Component> getTooltipText() {
        if (io.github.wkwk.wei.data.WynnCatalog.isTooltipFallback(key) && RETRIED.add(key)) {
            logHoverRetry();
        }
        return tooltip;
    }

    /** 原因調査用: 一覧作成時に説明文が作れなかったアイテムに、ゲーム中のいま作れるかを試してログに出す（表示は変えない） */
    private void logHoverRetry() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        try {
            List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, mc.player,
                    net.minecraft.world.item.TooltipFlag.NORMAL);
            io.github.wkwk.wei.Wei.LOGGER.warn("[WEI tooltip] HOVER retry key='{}' player={} -> {} line(s), first='{}'",
                    key, mc.player != null, lines.size(), lines.isEmpty() ? "" : lines.get(0).getString());
        } catch (Throwable t) {
            io.github.wkwk.wei.Wei.LOGGER.warn("[WEI tooltip] HOVER retry key='{}' player={} FAILED", key, mc.player != null, t);
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

    /** Identifier に使えない文字を置き換えると別のキーが同じになり得るので、元のキーのハッシュを足す。 */
    private static String toPath(String key) {
        return Names.idPath(key) + "_" + Integer.toHexString(key.hashCode());
    }
}

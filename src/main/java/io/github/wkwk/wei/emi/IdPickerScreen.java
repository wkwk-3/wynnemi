package io.github.wkwk.wei.emi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ID を一覧から選ぶ画面。上で絞り込み・並び順（A→Z / Z→A）を切り替え、押した ID を検索画面の条件に足す。
 */
final class IdPickerScreen extends Screen {
    private static final int ROW = 20;
    private static final int COLUMNS = 3;

    private final Screen parent;
    private final List<String> allIds;
    private final Consumer<String> onPick;

    /** 画面を開き直しても並び順は覚えておく */
    private static boolean descending;
    private String filter = "";
    private int page;

    IdPickerScreen(Screen parent, List<String> ids, Consumer<String> onPick) {
        super(Component.translatable("wei.idpicker.title"));
        this.parent = parent;
        this.allIds = ids;
        this.onPick = onPick;
    }

    private List<String> visibleIds() {
        String needle = filter.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<String> out = new ArrayList<>();
        for (String id : allIds) if (needle.isEmpty() || id.contains(needle)) out.add(id);
        Comparator<String> order = Comparator.naturalOrder();
        out.sort(descending ? order.reversed() : order);
        return out;
    }

    @Override
    protected void init() {
        int listW = Math.min(width - 20, 420);
        int left = (width - listW) / 2;
        int top = 26;

        EditBox search = new EditBox(font, left, top, listW - 96, 18, Component.empty());
        search.setHint(Component.translatable("wei.idpicker.filter"));
        search.setValue(filter);
        // 入力のたびに画面ごと作り直すと入力欄のカーソルが飛ぶので、一覧のボタンだけ作り直す
        search.setResponder(v -> {
            if (!v.equals(filter)) {
                filter = v;
                page = 0;
                refreshList();
            }
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.translatable(descending ? "wei.idpicker.desc" : "wei.idpicker.asc"), b -> {
            descending = !descending;
            page = 0;
            rebuildWidgets();
        }).bounds(left + listW - 92, top, 92, 18).build());

        int by = height - 26;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + pages - 1) % pages;
            refreshList();
        }).bounds(left, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % pages;
            refreshList();
        }).bounds(left + 24, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 50, by, 100, 20).build());
        refreshList();
    }

    private final List<Button> listButtons = new ArrayList<>();

    private void refreshList() {
        for (Button b : listButtons) removeWidget(b);
        listButtons.clear();
        int listW = Math.min(width - 20, 420);
        int left = (width - listW) / 2;
        List<String> ids = visibleIds();
        int listTop = 26 + 26;
        int rows = Math.max(1, (height - listTop - 36) / ROW);
        int perPage = rows * COLUMNS;
        pages = Math.max(1, (ids.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        count = ids.size();
        int cellW = (listW - (COLUMNS - 1) * 4) / COLUMNS;
        for (int i = page * perPage; i < Math.min(ids.size(), (page + 1) * perPage); i++) {
            String id = ids.get(i);
            int n = i - page * perPage;
            int col = n / rows;
            int row = n % rows;
            String shown = font.plainSubstrByWidth(id, cellW - 8);
            listButtons.add(addRenderableWidget(Button.builder(Component.literal(shown), b -> {
                onPick.accept(id);
                minecraft.setScreen(parent);
            }).bounds(left + col * (cellW + 4), listTop + row * ROW, cellW, 18).build()));
        }
    }

    private int pages = 1;
    private int count;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        int listW = Math.min(width - 20, 420);
        int left = (width - listW) / 2;
        g.drawString(font, Component.translatable("wei.idpicker.count", count, page + 1, pages), left + 50, height - 20, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

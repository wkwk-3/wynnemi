package io.github.wkwk.wei.emi;

import dev.emi.emi.api.EmiApi;
import io.github.wkwk.wei.data.WynnCatalog;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 公式サイトのアイテム検索（https://wynncraft.com/help/item-guide）のような条件設定画面。
 * 選んだ条件を WEI の検索語（type: tier: levelRange: など）にして EMI の検索欄に入れる。通信はしない。
 */
public final class WeiSearchScreen extends Screen {
    private static final String[] TYPES = {"", "weapon", "armour", "accessory", "tome", "charm", "tool", "ingredient", "material"};
    private static final String[] ITEM_TIERS = {"normal", "unique", "rare", "legendary", "fabled", "mythic"};
    private static final String[] INGREDIENT_TIERS = {"tier_0", "tier_1", "tier_2", "tier_3"};
    private static final String[] ATTACK_SPEEDS = {"", "superSlow", "verySlow", "slow", "normal", "fast", "veryFast", "superFast"};
    private static final String[] CRAFTING = {"", "alchemism", "armouring", "cooking", "jeweling", "scribing", "tailoring", "weaponsmithing", "woodworking"};
    private static final String[] GATHERING = {"", "mining", "fishing", "farming", "woodcutting"};

    private static String[] subTypes(String type) {
        return switch (type) {
            case "weapon" -> new String[] {"", "bow", "relik", "wand", "dagger", "spear"};
            case "armour" -> new String[] {"", "helmet", "chestplate", "leggings", "boots"};
            case "accessory" -> new String[] {"", "necklace", "ring", "bracelet"};
            case "tome" -> new String[] {"", "weaponTome", "armourTome", "guildTome", "expertiseTome", "mysticismTome", "marathonTome", "lootrunTome"};
            case "tool" -> new String[] {"", "axe", "pickaxe", "rod", "scythe"};
            default -> new String[] {""};
        };
    }

    private final @Nullable Screen parent;

    // 選択状態（画面を作り直しても残す）
    private String type = "";
    private String subType = "";
    private final Set<String> tiers = new LinkedHashSet<>();
    private String attackSpeed = "";
    private String profession = "";
    private String name = "";
    private String levelMin = "";
    private String levelMax = "";
    private String ids = "";
    private String majorIds = "";
    /** 検索欄にあった WEI 以外の語（名前検索など）は残す */
    private String otherTokens = "";

    private EditBox nameBox;
    private EditBox minBox;
    private EditBox maxBox;
    private EditBox idBox;
    private EditBox majorBox;
    private @Nullable EditBox suggestTarget;
    private final List<Button> suggestButtons = new ArrayList<>();
    private List<String> knownIds = List.of();
    private List<String> knownMajorIds = List.of();

    public WeiSearchScreen(@Nullable Screen parent) {
        super(Component.translatable("wei.search.title"));
        this.parent = parent;
        readCurrentSearch();
        collectKnownNames();
    }

    @Override
    protected void init() {
        int left = width / 2 - 160;
        int y = 28;
        int labelW = 70;
        int x = left + labelW;

        nameBox = new EditBox(font, x, y, 250, 18, Component.translatable("wei.search.name"));
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        y += 24;

        addRenderableWidget(Button.builder(label(type, "wei.search.any"), b -> {
            type = next(TYPES, type);
            subType = "";
            attackSpeed = "";
            profession = "";
            tiers.clear();
            rebuildWidgets();
        }).bounds(x, y, 120, 20).build());
        String[] subs = subTypes(type);
        if (subs.length > 1) {
            addRenderableWidget(Button.builder(label(subType, "wei.search.any"), b -> {
                subType = next(subTypes(type), subType);
                rebuildWidgets();
            }).bounds(x + 126, y, 124, 20).build());
        }
        y += 24;

        // ティア（複数選べる）
        String[] tierList = type.equals("ingredient") ? INGREDIENT_TIERS
                : type.equals("material") || type.equals("tool") ? new String[0] : ITEM_TIERS;
        int tx = x;
        int ty = y;
        for (String tier : tierList) {
            int w = Math.max(30, font.width(tier) + 10);
            if (tx + w > x + 250) {
                tx = x;
                ty += 22;
            }
            boolean on = tiers.contains(tier);
            addRenderableWidget(Button.builder(Component.literal((on ? "§a✔ " : "") + tier), b -> {
                if (!tiers.remove(tier)) tiers.add(tier);
                rebuildWidgets();
            }).bounds(tx, ty, w + (on ? 10 : 0), 20).build());
            tx += w + (on ? 10 : 0) + 2;
        }
        y = (tierList.length == 0 ? y : ty + 24);

        minBox = new EditBox(font, x, y, 50, 18, Component.translatable("wei.search.level"));
        minBox.setValue(levelMin);
        minBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        minBox.setResponder(v -> levelMin = v);
        addRenderableWidget(minBox);
        maxBox = new EditBox(font, x + 70, y, 50, 18, Component.translatable("wei.search.level"));
        maxBox.setValue(levelMax);
        maxBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        maxBox.setResponder(v -> levelMax = v);
        addRenderableWidget(maxBox);
        y += 24;

        if (type.equals("weapon") || type.isEmpty()) {
            addRenderableWidget(Button.builder(label(attackSpeed, "wei.search.any"), b -> {
                attackSpeed = next(ATTACK_SPEEDS, attackSpeed);
                rebuildWidgets();
            }).bounds(x, y, 120, 20).build());
            y += 24;
        }
        if (type.equals("ingredient") || type.equals("material") || type.equals("tool") || type.isEmpty()) {
            String[] list = type.equals("ingredient") ? CRAFTING
                    : type.equals("material") || type.equals("tool") ? GATHERING : concat(CRAFTING, GATHERING);
            addRenderableWidget(Button.builder(label(profession, "wei.search.any"), b -> {
                profession = next(list, profession);
                rebuildWidgets();
            }).bounds(x, y, 120, 20).build());
            y += 24;
        }

        idBox = new EditBox(font, x, y, 250, 18, Component.translatable("wei.search.ids"));
        idBox.setValue(ids);
        idBox.setHint(Component.literal("rawStrength, walkSpeed"));
        idBox.setResponder(v -> {
            ids = v;
            suggestTarget = idBox;
            updateSuggestions();
        });
        addRenderableWidget(idBox);
        y += 22;
        majorBox = new EditBox(font, x, y, 250, 18, Component.translatable("wei.search.majorIds"));
        majorBox.setValue(majorIds);
        majorBox.setResponder(v -> {
            majorIds = v;
            suggestTarget = majorBox;
            updateSuggestions();
        });
        addRenderableWidget(majorBox);
        y += 22;

        // 入力中の ID の候補（押すと入る）
        suggestButtons.clear();
        for (int i = 0; i < 4; i++) {
            Button b = Button.builder(Component.empty(), btn -> applySuggestion(btn.getMessage().getString()))
                    .bounds(x + i * 64, y, 62, 16).build();
            b.visible = false;
            suggestButtons.add(addRenderableWidget(b));
        }
        y += 22;

        addRenderableWidget(Button.builder(Component.translatable("wei.search.apply"), b -> apply())
                .bounds(left + 40, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("wei.search.clear"), b -> {
            type = subType = attackSpeed = profession = name = levelMin = levelMax = ids = majorIds = "";
            tiers.clear();
            rebuildWidgets();
        }).bounds(left + 136, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 232, y, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = width / 2 - 160;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        int y = 33;
        g.drawString(font, Component.translatable("wei.search.name"), left, y, 0xFFE0E0E0);
        y += 24;
        g.drawString(font, Component.translatable("wei.search.type"), left, y + 1, 0xFFE0E0E0);
        if (minBox != null) {
            String[] tierList = type.equals("ingredient") ? INGREDIENT_TIERS
                    : type.equals("material") || type.equals("tool") ? new String[0] : ITEM_TIERS;
            if (tierList.length > 0) g.drawString(font, Component.translatable("wei.search.tier"), left, y + 25, 0xFFE0E0E0);
            g.drawString(font, Component.translatable("wei.search.level"), left, minBox.getY() + 5, 0xFFE0E0E0);
            g.drawString(font, "〜", minBox.getX() + 56, minBox.getY() + 5, 0xFFE0E0E0);
            g.drawString(font, Component.translatable("wei.search.ids"), left, idBox.getY() + 5, 0xFFE0E0E0);
            g.drawString(font, Component.translatable("wei.search.majorIds"), left, majorBox.getY() + 5, 0xFFE0E0E0);
            int optY = minBox.getY() + 24;
            if (type.equals("weapon") || type.isEmpty()) {
                g.drawString(font, Component.translatable("wei.search.attackSpeed"), left, optY + 6, 0xFFE0E0E0);
                optY += 24;
            }
            if (type.equals("ingredient") || type.equals("material") || type.equals("tool") || type.isEmpty()) {
                g.drawString(font, Component.translatable("wei.search.profession"), left, optY + 6, 0xFFE0E0E0);
            }
        }
        g.drawCenteredString(font, Component.literal(buildQuery()), width / 2, height - 14, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void apply() {
        String query = buildQuery();
        onClose();
        EmiApi.setSearchText(query);
    }

    /** 画面の選択を WEI の検索語にする */
    String buildQuery() {
        List<String> parts = new ArrayList<>();
        if (!otherTokens.isBlank()) parts.add(otherTokens.trim());
        if (!name.isBlank()) parts.add(name.trim());
        if (!subType.isEmpty()) parts.add("type:" + subType);
        else if (!type.isEmpty()) parts.add("type:" + type);
        if (!tiers.isEmpty()) parts.add("tier:" + String.join(",", tiers));
        if (!attackSpeed.isEmpty()) parts.add("attackSpeed:" + attackSpeed);
        if (!profession.isEmpty()) parts.add("professions:" + profession);
        if (!levelMin.isEmpty() && !levelMax.isEmpty()) parts.add("levelRange:" + levelMin + "-" + levelMax);
        else if (!levelMin.isEmpty()) parts.add("levelRange:>=" + levelMin);
        else if (!levelMax.isEmpty()) parts.add("levelRange:<=" + levelMax);
        String idList = list(ids);
        if (!idList.isEmpty()) parts.add("identifications:" + idList);
        String majorList = list(majorIds);
        if (!majorList.isEmpty()) parts.add("majorIds:" + majorList);
        return String.join(" ", parts);
    }

    /** いまの検索欄を読み、分かる語は画面に、分からない語はそのまま残す */
    private void readCurrentSearch() {
        String current;
        try {
            current = EmiApi.getSearchText();
        } catch (Throwable t) {
            current = "";
        }
        if (current == null) return;
        List<String> rest = new ArrayList<>();
        for (String token : current.trim().split("\\s+")) {
            int colon = token.indexOf(':');
            String key = colon < 0 ? "" : token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : token.substring(colon + 1);
            switch (key) {
                case "type" -> {
                    if (indexOf(TYPES, value) > 0) type = value;
                    else for (String t : TYPES) if (indexOf(subTypes(t), value) > 0) {
                        type = t;
                        subType = subTypes(t)[indexOf(subTypes(t), value)];
                    }
                }
                case "tier" -> {
                    for (String v : value.split(",")) if (!v.isBlank()) tiers.add(v.toLowerCase(Locale.ROOT));
                }
                case "attackspeed" -> attackSpeed = value;
                case "professions" -> profession = value;
                case "levelrange" -> {
                    if (value.startsWith(">=")) levelMin = value.substring(2);
                    else if (value.startsWith("<=")) levelMax = value.substring(2);
                    else if (value.contains("-")) {
                        levelMin = value.substring(0, value.indexOf('-'));
                        levelMax = value.substring(value.indexOf('-') + 1);
                    } else levelMin = levelMax = value;
                }
                case "identifications" -> ids = value.replace(",", ", ");
                case "majorids" -> majorIds = value.replace(",", ", ");
                default -> {
                    if (!token.isEmpty()) rest.add(token);
                }
            }
        }
        name = String.join(" ", rest);
    }

    private void collectKnownNames() {
        Set<String> idNames = new TreeSet<>();
        Set<String> majors = new TreeSet<>();
        for (WynnCatalog.Entry e : WynnCatalog.current().entries()) {
            idNames.addAll(e.facets().identifications().keySet());
            majors.addAll(e.facets().majorIds());
        }
        knownIds = List.copyOf(idNames);
        knownMajorIds = List.copyOf(majors);
    }

    private void updateSuggestions() {
        String text = suggestTarget == null ? "" : suggestTarget.getValue();
        String last = text.contains(",") ? text.substring(text.lastIndexOf(',') + 1) : text;
        String needle = last.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<String> pool = suggestTarget == majorBox ? knownMajorIds : knownIds;
        List<String> hits = new ArrayList<>();
        if (!needle.isEmpty()) {
            for (String n : pool) {
                if (n.startsWith(needle)) hits.add(n);
                if (hits.size() >= suggestButtons.size()) break;
            }
            for (String n : pool) {
                if (hits.size() >= suggestButtons.size()) break;
                if (!hits.contains(n) && n.contains(needle)) hits.add(n);
            }
        }
        for (int i = 0; i < suggestButtons.size(); i++) {
            Button b = suggestButtons.get(i);
            b.visible = i < hits.size();
            if (b.visible) b.setMessage(Component.literal(hits.get(i)));
        }
    }

    private void applySuggestion(String value) {
        if (suggestTarget == null) return;
        String text = suggestTarget.getValue();
        int comma = text.lastIndexOf(',');
        String head = comma < 0 ? "" : text.substring(0, comma + 1) + " ";
        suggestTarget.setValue(head + value + ", ");
        setFocused(suggestTarget);
    }

    private static String list(String raw) {
        List<String> out = new ArrayList<>();
        for (String v : raw.split(",")) if (!v.isBlank()) out.add(v.trim().replace(" ", ""));
        return String.join(",", out);
    }

    private static Component label(String value, String emptyKey) {
        return value.isEmpty() ? Component.translatable(emptyKey) : Component.literal(value);
    }

    private static String next(String[] values, String current) {
        int i = indexOf(values, current);
        return values[(i + 1) % values.length];
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(value)) return i;
        return -1;
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length - 1];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 1, out, a.length, b.length - 1);
        return out;
    }
}

package io.github.wkwk.wei.emi;

import dev.emi.emi.api.EmiApi;
import io.github.wkwk.wei.data.WynnCatalog;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Wynncraft 用の条件検索。選んだ条件を WEI の検索語にして EMI の検索欄に入れる。
 *
 * <pre>
 *  左: 名前 / 種類・詳細 / ティア / レベル / 攻撃速度 / 職業 / 必要スキル
 *  右: スキルボーナス / ID の条件 / メジャー ID
 *  下: 検索語のプレビュー / ボタン
 * </pre>
 */
public final class WeiSearchScreen extends Screen {
    private static final String[] TYPES = {"", "weapon", "armour", "accessory", "tome", "charm", "tool", "ingredient", "material"};
    private static final String[] ITEM_TIERS = {"normal", "unique", "rare", "legendary", "fabled", "mythic"};
    private static final String[] INGREDIENT_TIERS = {"tier_0", "tier_1", "tier_2", "tier_3"};
    private static final String[] ATTACK_SPEEDS = {"", "superSlow", "verySlow", "slow", "normal", "fast", "veryFast", "superFast"};
    private static final String[] CRAFTING = {"", "alchemism", "armouring", "cooking", "jeweling", "scribing", "tailoring", "weaponsmithing", "woodworking"};
    private static final String[] GATHERING = {"", "mining", "fishing", "farming", "woodcutting"};
    /** 必要スキルのキー接頭辞（strReq など）とスキルボーナスの ID 名。順番は対応 */
    private static final String[] SKILLS = {"str", "dex", "int", "def", "agi"};
    private static final String[] SKILL_BONUS_IDS = {"rawStrength", "rawDexterity", "rawIntelligence", "rawDefence", "rawAgility"};

    private static final int ROW = 20;
    private static final int LABEL = 0xFFE0E0E0;
    private static final int HEADER = 0xFFFFD27F;

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

    // 選択状態（rebuildWidgets 後も保持）
    private String type = "";
    private String subType = "";
    private final Set<String> tiers = new LinkedHashSet<>();
    private String attackSpeed = "";
    private String profession = "";
    private String name = "";
    private String levelMin = "";
    private String levelMax = "";
    private final String[] reqMin = {"", "", "", "", ""};
    private final String[] reqMax = {"", "", "", "", ""};
    private final String[] bonusMin = {"", "", "", "", ""};
    private final String[] bonusMax = {"", "", "", "", ""};
    /** ID の条件。範囲が空なら「その ID を持つ」 */
    static final class IdFilter {
        final String id;
        String min = "";
        String max = "";

        IdFilter(String id) {
            this.id = id;
        }
    }

    private final List<IdFilter> idFilters = new ArrayList<>();
    private String majorIds = "";

    private EditBox majorBox;
    private @Nullable EditBox suggestTarget;
    private final List<Button> suggestButtons = new ArrayList<>();
    private List<String> knownIds = List.of();
    private List<String> knownMajorIds = List.of();

    private final List<Label> labels = new ArrayList<>();

    private record Label(Component text, int x, int y, int color) {}

    public WeiSearchScreen(@Nullable Screen parent) {
        super(Component.translatable("wei.search.title"));
        this.parent = parent;
        readCurrentSearch();
        collectKnownNames();
    }

    private boolean showsSkills() {
        return type.isEmpty() || type.equals("weapon") || type.equals("armour") || type.equals("accessory");
    }

    @Override
    protected void init() {
        labels.clear();
        int colW = 190;
        int leftX = width / 2 - colW - 6;
        int rightX = width / 2 + 6;
        int fieldX = leftX + 58;
        int fieldW = colW - 58;
        int top = 26;

        // ---- 左
        int y = top;
        label("wei.search.name", leftX, y);
        box(fieldX, y, fieldW, name, v -> name = v, Component.translatable("wei.search.name.hint"));
        y += ROW;

        label("wei.search.type", leftX, y);
        addRenderableWidget(Button.builder(label(type, "wei.search.any"), b -> {
            type = next(TYPES, type);
            subType = attackSpeed = profession = "";
            tiers.clear();
            rebuildWidgets();
        }).bounds(fieldX, y, fieldW / 2 - 1, 18).build());
        if (subTypes(type).length > 1) {
            addRenderableWidget(Button.builder(label(subType, "wei.search.any"), b -> {
                subType = next(subTypes(type), subType);
                b.setMessage(label(subType, "wei.search.any"));
            }).bounds(fieldX + fieldW / 2 + 1, y, fieldW / 2 - 1, 18).build());
        }
        y += ROW;

        String[] tierList = tierList();
        if (tierList.length > 0) {
            label("wei.search.tier", leftX, y);
            int tx = fieldX;
            for (String tier : tierList) {
                boolean on = tiers.contains(tier);
                int w = font.width((on ? "✔ " : "") + tier) + 8;
                if (tx + w > fieldX + fieldW) {
                    tx = fieldX;
                    y += 20;
                }
                addRenderableWidget(Button.builder(Component.literal((on ? "§a✔ " : "") + tier), b -> {
                    if (!tiers.remove(tier)) tiers.add(tier);
                    rebuildWidgets();
                }).bounds(tx, y, w, 18).build());
                tx += w + 2;
            }
            y += ROW;
        }

        label("wei.search.level", leftX, y);
        rangeBoxes(fieldX, y, () -> levelMin, v -> levelMin = v, () -> levelMax, v -> levelMax = v, false);
        y += ROW;

        if (type.isEmpty() || type.equals("weapon")) {
            label("wei.search.attackSpeed", leftX, y);
            addRenderableWidget(Button.builder(label(attackSpeed, "wei.search.any"), b -> {
                attackSpeed = next(ATTACK_SPEEDS, attackSpeed);
                b.setMessage(label(attackSpeed, "wei.search.any"));
            }).bounds(fieldX, y, fieldW, 18).build());
            y += ROW;
        }
        if (type.isEmpty() || type.equals("ingredient") || type.equals("material") || type.equals("tool")) {
            String[] list = type.equals("ingredient") ? CRAFTING
                    : type.equals("material") || type.equals("tool") ? GATHERING : concat(CRAFTING, GATHERING);
            label("wei.search.profession", leftX, y);
            addRenderableWidget(Button.builder(label(profession, "wei.search.any"), b -> {
                profession = next(list, profession);
                b.setMessage(label(profession, "wei.search.any"));
            }).bounds(fieldX, y, fieldW, 18).build());
        }

        int skillLabelW = 46;
        if (showsSkills()) {
            y += ROW + 2;
            labels.add(new Label(Component.translatable("wei.search.skillReq"), leftX, y + 4, HEADER));
            y += 14;
            for (int i = 0; i < SKILLS.length; i++) {
                int index = i;
                label("wei.search.skill." + SKILLS[i], leftX, y);
                rangeBoxes(fieldX, y, () -> reqMin[index], v -> reqMin[index] = v,
                        () -> reqMax[index], v -> reqMax[index] = v, false);
                y += 18;
            }
        }

        // ---- 右
        int ry = top;
        if (showsSkills()) {
            labels.add(new Label(Component.translatable("wei.search.skillBonus"), rightX, ry + 4, HEADER));
            ry += 14;
            for (int i = 0; i < SKILLS.length; i++) {
                int index = i;
                label("wei.search.skill." + SKILLS[i], rightX, ry);
                rangeBoxes(rightX + skillLabelW, ry, () -> bonusMin[index], v -> bonusMin[index] = v,
                        () -> bonusMax[index], v -> bonusMax[index] = v, true);
                ry += 18;
            }
            ry += 6;
        }
        labels.add(new Label(Component.translatable("wei.search.ids"), rightX, ry + 4, HEADER));
        addRenderableWidget(Button.builder(Component.translatable("wei.search.addId"),
                        b -> minecraft.setScreen(new IdPickerScreen(this, knownIds, this::addIdFilter)))
                .bounds(rightX + colW - 70, ry, 70, 16).build());
        ry += 18;
        for (IdFilter filter : idFilters) {
            String shown = font.plainSubstrByWidth(filter.id, colW - 132);
            labels.add(new Label(Component.literal(shown.length() < filter.id.length() ? shown + "…" : shown),
                    rightX, ry + 5, LABEL));
            int bx = rightX + colW - 128;
            rangeBoxes(bx, ry, () -> filter.min, v -> filter.min = v, () -> filter.max, v -> filter.max = v, true);
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                idFilters.remove(filter);
                rebuildWidgets();
            }).bounds(rightX + colW - 18, ry, 18, 18).build());
            ry += 18;
        }
        if (idFilters.isEmpty()) {
            labels.add(new Label(Component.translatable("wei.search.ids.empty"), rightX, ry + 5, 0xFF808080));
            ry += 18;
        }
        ry += 4;
        label("wei.search.majorIds", rightX, ry);
        majorBox = box(rightX + skillLabelW, ry, colW - skillLabelW, majorIds, v -> {
            majorIds = v;
            suggestTarget = majorBox;
            updateSuggestions();
        }, null);
        ry += ROW;
        // 入力中のメジャー ID の候補
        suggestButtons.clear();
        int sw = (colW - 6) / 4;
        for (int i = 0; i < 4; i++) {
            Button b = Button.builder(Component.empty(), btn -> applySuggestion(btn.getMessage().getString()))
                    .bounds(rightX + i * (sw + 2), ry, sw, 16).build();
            b.visible = false;
            suggestButtons.add(addRenderableWidget(b));
        }

        // ---- 下
        int by = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("wei.search.apply"), b -> apply())
                .bounds(width / 2 - 154, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("wei.search.clear"), b -> {
            type = subType = attackSpeed = profession = name = levelMin = levelMax = majorIds = "";
            idFilters.clear();
            tiers.clear();
            for (int i = 0; i < 5; i++) reqMin[i] = reqMax[i] = bonusMin[i] = bonusMax[i] = "";
            rebuildWidgets();
        }).bounds(width / 2 - 50, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 + 54, by, 100, 20).build());
    }

    private String[] tierList() {
        if (type.equals("ingredient")) return INGREDIENT_TIERS;
        if (type.equals("material") || type.equals("tool")) return new String[0];
        return ITEM_TIERS;
    }

    private void label(String key, int x, int y) {
        labels.add(new Label(Component.translatable(key), x, y + 5, LABEL));
    }

    private EditBox box(int x, int y, int w, String value, Consumer<String> onChange, @Nullable Component hint) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        b.setMaxLength(256);
        b.setValue(value);
        b.setResponder(onChange);
        if (hint != null) b.setHint(hint);
        addRenderableWidget(b);
        return b;
    }

    /** 下限・上限の入力欄 */
    private void rangeBoxes(int x, int y, Supplier<String> min, Consumer<String> setMin,
            Supplier<String> max, Consumer<String> setMax, boolean allowNegative) {
        String pattern = allowNegative ? "-?[0-9]*" : "[0-9]*";
        EditBox a = box(x, y, 44, min.get(), setMin, Component.translatable("wei.search.min"));
        a.setFilter(s -> s.matches(pattern));
        labels.add(new Label(Component.literal("〜"), x + 48, y + 5, LABEL));
        EditBox b = box(x + 60, y, 44, max.get(), setMax, Component.translatable("wei.search.max"));
        b.setFilter(s -> s.matches(pattern));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color());
        String query = buildQuery();
        g.drawCenteredString(font, Component.literal(query.isEmpty() ? "-" : query), width / 2, height - 40, 0xFFAAAAAA);
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

    String buildQuery() {
        List<String> parts = new ArrayList<>();
        if (!name.isBlank()) parts.add(name.trim());
        if (!subType.isEmpty()) parts.add("type:" + subType);
        else if (!type.isEmpty()) parts.add("type:" + type);
        if (!tiers.isEmpty()) parts.add("tier:" + String.join(",", tiers));
        if (!attackSpeed.isEmpty()) parts.add("attackSpeed:" + attackSpeed);
        if (!profession.isEmpty()) parts.add("professions:" + profession);
        addRange(parts, "levelRange", levelMin, levelMax);
        if (showsSkills()) {
            for (int i = 0; i < SKILLS.length; i++) {
                addRange(parts, SKILLS[i] + "Req", reqMin[i], reqMax[i]);
                addRange(parts, "id." + SKILL_BONUS_IDS[i], bonusMin[i], bonusMax[i]);
            }
        }
        List<String> has = new ArrayList<>();
        for (IdFilter f : idFilters) {
            if (isNumber(f.min) || isNumber(f.max)) addRange(parts, "id." + f.id, f.min, f.max);
            else has.add(f.id);
        }
        if (!has.isEmpty()) parts.add("identifications:" + String.join(",", has));
        String majorList = list(majorIds);
        if (!majorList.isEmpty()) parts.add("majorIds:" + majorList);
        return String.join(" ", parts);
    }

    /** 片方だけなら以上・以下。逆順は入れ替える */
    private static void addRange(List<String> parts, String key, String min, String max) {
        boolean hasMin = isNumber(min);
        boolean hasMax = isNumber(max);
        if (hasMin && hasMax) {
            int a = Integer.parseInt(min);
            int b = Integer.parseInt(max);
            parts.add(key + ":" + Math.min(a, b) + "-" + Math.max(a, b));
        } else if (hasMin) {
            parts.add(key + ":>=" + min);
        } else if (hasMax) {
            parts.add(key + ":<=" + max);
        }
    }

    private static boolean isNumber(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 現在の検索欄を読み込む。解釈できない語は名前欄に残す */
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
            if (readSkill(key, value)) continue;
            switch (key) {
                case "type" -> {
                    if (indexOf(TYPES, value) > 0) {
                        type = TYPES[indexOf(TYPES, value)];
                    } else {
                        for (String t : TYPES) {
                            int i = indexOf(subTypes(t), value);
                            if (i > 0) {
                                type = t;
                                subType = subTypes(t)[i];
                            }
                        }
                    }
                }
                case "tier" -> {
                    for (String v : value.split(",")) if (!v.isBlank()) tiers.add(v.toLowerCase(Locale.ROOT));
                }
                case "attackspeed" -> attackSpeed = value;
                case "professions" -> profession = value;
                case "levelrange" -> {
                    String[] r = readRange(value);
                    levelMin = r[0];
                    levelMax = r[1];
                }
                case "identifications" -> {
                    for (String v : value.split(",")) if (!v.isBlank()) addIdFilter(v.trim());
                }
                case "majorids" -> majorIds = value.replace(",", ", ");
                default -> {
                    if (!token.isEmpty()) rest.add(token);
                }
            }
        }
        name = String.join(" ", rest);
    }

    private void addIdFilter(String id) {
        for (IdFilter f : idFilters) if (f.id.equalsIgnoreCase(id)) return;
        idFilters.add(new IdFilter(id));
    }

    private boolean readSkill(String key, String value) {
        for (int i = 0; i < SKILLS.length; i++) {
            if (key.equals(SKILLS[i] + "req")) {
                String[] r = readRange(value);
                reqMin[i] = r[0];
                reqMax[i] = r[1];
                return true;
            }
            if (key.equals("id." + SKILL_BONUS_IDS[i].toLowerCase(Locale.ROOT))) {
                String[] r = readRange(value);
                bonusMin[i] = r[0];
                bonusMax[i] = r[1];
                return true;
            }
        }
        if (key.startsWith("id.") && key.length() > 3) {
            String[] r = readRange(value);
            addIdFilter(key.substring(3));
            IdFilter f = idFilters.get(idFilters.size() - 1);
            for (IdFilter x : idFilters) if (x.id.equalsIgnoreCase(key.substring(3))) f = x;
            f.min = r[0];
            f.max = r[1];
            return true;
        }
        return false;
    }

    private static String[] readRange(String value) {
        if (value.startsWith(">=")) return new String[] {value.substring(2), ""};
        if (value.startsWith("<=")) return new String[] {"", value.substring(2)};
        int dash = value.indexOf('-', 1);
        while (dash > 0 && value.charAt(dash - 1) == '-') dash = value.indexOf('-', dash + 1);
        if (dash > 0) return new String[] {value.substring(0, dash), value.substring(dash + 1)};
        return new String[] {value, value};
    }

    /** 候補はカタログが同じ間は使い回す */
    private static WynnCatalog cachedFor;
    private static List<String> cachedIds = List.of();
    private static List<String> cachedMajorIds = List.of();

    private void collectKnownNames() {
        WynnCatalog catalog = WynnCatalog.current();
        if (catalog == cachedFor) {
            knownIds = cachedIds;
            knownMajorIds = cachedMajorIds;
            return;
        }
        Set<String> idNames = new TreeSet<>();
        Set<String> majors = new TreeSet<>();
        for (WynnCatalog.Entry e : catalog.entries()) {
            idNames.addAll(e.facets().identifications().keySet());
            majors.addAll(e.facets().majorIds());
        }
        knownIds = cachedIds = List.copyOf(idNames);
        knownMajorIds = cachedMajorIds = List.copyOf(majors);
        cachedFor = catalog;
    }

    private void updateSuggestions() {
        String text = suggestTarget == null ? "" : suggestTarget.getValue();
        String last = text.contains(",") ? text.substring(text.lastIndexOf(',') + 1) : text;
        String needle = last.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<String> pool = suggestTarget == majorBox ? knownMajorIds : knownIds;
        List<String> hits = new ArrayList<>();
        if (!needle.isEmpty()) {
            for (String n : pool) {
                if (hits.size() >= suggestButtons.size()) break;
                if (n.startsWith(needle)) hits.add(n);
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

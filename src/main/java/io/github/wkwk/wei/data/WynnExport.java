package io.github.wkwk.wei.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.wkwk.wei.util.Names;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/** /wei export: いま EMI の一覧に出しているアイテムをすべて config/wei-export.json に書き出す */
public final class WynnExport {
    private WynnExport() {}

    public static Path export() throws IOException {
        WynnCatalog catalog = WynnCatalog.current();
        JsonArray items = new JsonArray();
        for (WynnCatalog.Entry e : catalog.entries()) {
            JsonObject o = new JsonObject();
            o.addProperty("key", e.key());
            o.addProperty("group", e.group());
            o.addProperty("name", e.plainName());
            o.addProperty("displayName", text(e.displayName()));
            o.addProperty("level", e.level());
            WynnCatalog.ApiFacets f = e.facets();
            o.addProperty("type", f.type());
            o.addProperty("subType", f.subType());
            o.addProperty("tier", f.tier());
            o.addProperty("attackSpeed", f.attackSpeed());
            JsonArray profs = new JsonArray();
            f.professions().forEach(profs::add);
            o.add("professions", profs);
            JsonObject ids = new JsonObject();
            for (Map.Entry<String, Integer> id : f.identifications().entrySet()) ids.addProperty(id.getKey(), id.getValue());
            o.add("identifications", ids);
            JsonArray majors = new JsonArray();
            f.majorIds().forEach(majors::add);
            o.add("majorIds", majors);
            JsonArray obtain = new JsonArray();
            for (WynnCatalog.ObtainSnapshot s : e.obtain()) {
                JsonObject so = new JsonObject();
                so.addProperty("type", s.type() == null ? null : s.type().name());
                so.addProperty("source", s.sourceName());
                so.addProperty("caption", s.caption());
                obtain.add(so);
            }
            o.add("obtain", obtain);
            o.addProperty("requiredQuest", e.requiredQuest());
            o.addProperty("box", e.boxFamily() == null ? null : e.boxFamily().key());
            JsonArray tooltip = new JsonArray();
            for (Component line : e.tooltip()) tooltip.add(text(line));
            o.add("tooltip", tooltip);
            items.add(o);
        }
        JsonArray sets = new JsonArray();
        for (WynnCatalog.SetSnapshot s : catalog.sets()) {
            JsonObject so = new JsonObject();
            so.addProperty("name", s.name());
            JsonArray members = new JsonArray();
            s.memberKeys().forEach(members::add);
            so.add("members", members);
            JsonArray bonus = new JsonArray();
            for (Component line : s.bonusTooltip()) bonus.add(text(line));
            so.add("bonus", bonus);
            sets.add(so);
        }
        JsonObject root = new JsonObject();
        root.addProperty("count", items.size());
        root.add("items", items);
        root.add("sets", sets);
        Path out = FabricLoader.getInstance().getConfigDir().resolve("wei-export.json");
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(root),
                StandardCharsets.UTF_8);
        return out;
    }

    private static String text(Component c) {
        return Names.strip(c.getString());
    }
}

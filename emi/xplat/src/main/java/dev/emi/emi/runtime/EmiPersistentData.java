package dev.emi.emi.runtime;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.emi.emi.fork.EmiForkDiag;
import dev.emi.emi.bom.BoM;
import net.minecraft.util.JsonHelper;

public class EmiPersistentData {
	public static final File FILE = new File("emi.json");
	public static final Gson GSON = new Gson().newBuilder().setPrettyPrinting().create();
	
	public static void save() {
		try {
			JsonObject json = new JsonObject();
			json.add("favorites", EmiFavorites.save());
			// [WEI fork] diag#1: a save while EMI is not loaded can overwrite favorites that were dropped on load
			EmiForkDiag.log(1, "Persistent data SAVE: favorites=" + EmiFavorites.favorites.size() + " (WEI " + EmiForkDiag.countWei(json.get("favorites").toString())
				+ "), emiLoaded=" + EmiReloadManager.isLoaded() + ", status=" + EmiReloadManager.getStatus() + ", from " + EmiForkDiag.caller(3));
			EmiSidebars.save(json);
			json.add("recipe_defaults", BoM.saveAdded());
			json.add("hidden_stacks", EmiHidden.save());
			FileWriter writer = new FileWriter(FILE);
			GSON.toJson(json, writer);
			writer.close();
		} catch (Exception e) {
			EmiLog.error("Failed to write persistent data", e);
		}
	}

	public static void load() {
		if (!FILE.exists()) {
			return;
		}
		try {
			JsonObject json = GSON.fromJson(new FileReader(FILE), JsonObject.class);
			// [WEI fork] diag#1
			EmiForkDiag.log(1, "Persistent data LOAD: file favorites=" + (JsonHelper.hasArray(json, "favorites") ? JsonHelper.getArray(json, "favorites").size() : 0)
				+ " (WEI " + EmiForkDiag.countWei(String.valueOf(json.get("favorites"))) + "), hidden WEI=" + EmiForkDiag.countWei(String.valueOf(json.get("hidden_stacks"))));
			if (JsonHelper.hasArray(json, "favorites")) {
				EmiFavorites.load(JsonHelper.getArray(json, "favorites"));
			}
			EmiSidebars.load(json);
			if (JsonHelper.hasJsonObject(json, "recipe_defaults")) {
				BoM.loadAdded(JsonHelper.getObject(json, "recipe_defaults"));
			}
			if (JsonHelper.hasArray(json, "hidden_stacks")) {
				EmiHidden.load(JsonHelper.getArray(json, "hidden_stacks"));
			}
		} catch (Exception e) {
			EmiLog.error("Failed to parse persistent data", e);
		}
	}
}

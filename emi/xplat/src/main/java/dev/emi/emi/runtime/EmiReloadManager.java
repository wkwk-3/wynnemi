package dev.emi.emi.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import dev.emi.emi.fork.EmiForkDiag;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.mixinsupport.EmiMixinTransformation;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiComparisonDefaults;
import dev.emi.emi.registry.EmiDragDropHandlers;
import dev.emi.emi.registry.EmiExclusionAreas;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.registry.EmiInitRegistryImpl;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.registry.EmiRegistryImpl;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.registry.EmiStackProviders;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class EmiReloadManager {
	private static volatile int loadedResourcesMask = 0;
	// [WEI fork] diag#12: some servers (Wynncraft) only send one of the two sync signals, so EMI would wait forever.
	// If the other signal does not arrive within this delay, reload anyway.
	private static final long SYNC_FALLBACK_DELAY_MS = 3_000;
	private static volatile long syncGeneration = 0;
	private static volatile boolean clear = false, restart = false;
	// 0 - empty, 1 - reloading, 2 - loaded, -1 - error
	private static volatile int status = 0;
	private static Thread thread;
	public static volatile Text reloadStep = EmiPort.literal("");
	public static volatile long reloadWorry = Long.MAX_VALUE;

	static {
		EmiMixinTransformation.preach();
	}

	public static void reloadTags() {
		loadedResourcesMask |= 1;
		if (loadedResourcesMask == 3) {
			EmiLog.info("Recipes synchronized, reloading EMI");
			loadedResourcesMask = 0;
			syncGeneration++;
			EmiForkDiag.log(12, "Both sync signals received (tags last) -> normal reload");
			reload();
		} else {
			EmiLog.info("Recipes synchronized, waiting for tags to reload EMI...");
			scheduleSyncFallback("tags");
		}
	}

	public static void reloadRecipes() {
		loadedResourcesMask |= 2;
		if (loadedResourcesMask == 3) {
			EmiLog.info("Tags synchronized, reloading EMI");
			loadedResourcesMask = 0;
			syncGeneration++;
			EmiForkDiag.log(12, "Both sync signals received (recipes last) -> normal reload");
			reload();
		} else {
			EmiLog.info("Tags synchronized, waiting for recipes to reload EMI...");
			scheduleSyncFallback("recipes");
		}
	}

	// [WEI fork] diag#12: reload after a delay if only one of the two sync signals arrives
	private static void scheduleSyncFallback(String received) {
		long generation = ++syncGeneration;
		EmiForkDiag.log(12, "Only the " + received + " signal arrived; reloading in " + SYNC_FALLBACK_DELAY_MS + "ms unless the other one comes");
		Thread timer = new Thread(() -> {
			try {
				Thread.sleep(SYNC_FALLBACK_DELAY_MS);
			} catch (InterruptedException e) {
				return;
			}
			synchronized (EmiReloadManager.class) {
				if (generation != syncGeneration || loadedResourcesMask == 0 || loadedResourcesMask == 3) {
					EmiForkDiag.log(12, "Sync fallback cancelled (generation " + generation + "/" + syncGeneration + ", mask=" + loadedResourcesMask + ")");
					return;
				}
				EmiLog.info("Only one sync signal received (mask=" + loadedResourcesMask + "), reloading EMI anyway");
				EmiForkDiag.log(12, "Sync fallback FIRED (mask=" + loadedResourcesMask + ") -> reload");
				loadedResourcesMask = 0;
				syncGeneration++;
			}
			reload();
		}, "EMI sync fallback");
		timer.setDaemon(true);
		timer.start();
	}

	public static void clear() {
		synchronized (EmiReloadManager.class) {
			loadedResourcesMask = 0;
			syncGeneration++; // [WEI fork] cancel a pending sync fallback
			clear = true;
			status = 0;
			reloadWorry = Long.MAX_VALUE;
			if (thread != null && thread.isAlive()) {
				restart = true;
			} else {
				thread = new Thread(new ReloadWorker());
				thread.setDaemon(true);
				thread.start();
			}
		}
	}
	
	public static void reload() {
		// [WEI fork] diag#3: who requests EMI reloads, and how often
		EmiForkDiag.log(3, "EMI reload requested (running=" + (thread != null && thread.isAlive()) + ", status=" + status
			+ ", thread=" + Thread.currentThread().getName() + ") from " + EmiForkDiag.caller(5));
		synchronized (EmiReloadManager.class) {
			step(EmiPort.literal("Starting Reload"));
			status = 1;
			if (thread != null && thread.isAlive()) {
				restart = true;
			} else {
				clear = false;
				thread = new Thread(new ReloadWorker());
				thread.setDaemon(false);
				thread.start();
			}
		}
	}

	public static void step(Text text) {
		step(text, 5_000);
	}

	public static void step(Text text, long worry) {
		EmiLog.info(text.getString());
		reloadStep = text;
		reloadWorry = System.currentTimeMillis() + worry;
	}

	public static boolean isLoaded() {
		return status == 2 && (thread == null || !thread.isAlive());
	}

	public static int getStatus() {
		return status;
	}
	
	private static class ReloadWorker implements Runnable {

		@Override
		public void run() {
			int retries = 3;
			outer:
			do {
				try {
					if (!clear) {
						EmiLog.info("Starting EMI reload...");
					}
					long reloadStart = System.currentTimeMillis();
					restart = false;
					step(EmiPort.literal("Clearing data"));
					EmiRecipes.clear();
					EmiStackList.clear();
					EmiIngredientSerializers.clear();
					EmiExclusionAreas.clear();
					EmiDragDropHandlers.clear();
					EmiStackProviders.clear();
					EmiRecipeFiller.clear();
					EmiHidden.clear();
					EmiTags.ADAPTERS_BY_CLASS.map().clear();
					EmiTags.ADAPTERS_BY_REGISTRY.clear();
					EmiScreenBase.clearScreenBoundsProviders();
					if (clear) {
						clear = false;
						continue;
					}
					MinecraftClient client = MinecraftClient.getInstance();
					if (client.world == null) {
						EmiReloadLog.warn("World is null");
						break;
					} else if (client.world.getRecipeManager() == null) {
						EmiReloadLog.warn("Recipe Manager is null");
						break;
					}
					List<EmiPluginContainer> plugins = Lists.newArrayList();
					plugins.addAll(EmiAgnos.getPlugins().stream()
						.sorted((a, b) -> Integer.compare(entrypointPriority(a), entrypointPriority(b))).toList());
					
					if (EmiAgnos.isModLoaded("jei")) {
						plugins.add(new EmiPluginContainer(new JemiPlugin(), "jemi"));
					}
					EmiInitRegistry initRegistry = new EmiInitRegistryImpl();
					for (EmiPluginContainer container : plugins) {
						step(EmiPort.literal("Initializing plugin from " + container.id()), 5_000);
						long start = System.currentTimeMillis();
						try {
							container.plugin().initialize(initRegistry);
						} catch (Throwable e) {
							EmiReloadLog.warn("Exception initializing plugin provided by " + container.id(), e);
							if (restart) {
								continue outer;
							}
							continue;
						}
						EmiLog.info("Initialized plugin from " + container.id() + " in " + (System.currentTimeMillis() - start) + "ms");
					}
					EmiHidden.reload();

					step(EmiPort.literal("Processing tags"));
					EmiTags.reload();

					step(EmiPort.literal("Constructing index"));
					EmiComparisonDefaults.comparisons = new HashMap<>();
					EmiStackList.reload();
					if (restart) {
						continue;
					}
					EmiRegistry registry = new EmiRegistryImpl();
					
					for (EmiPluginContainer container : plugins) {
						step(EmiPort.literal("Loading plugin from " + container.id()), 10_000);
						long start = System.currentTimeMillis();
						try {
							container.plugin().register(registry);
						} catch (Throwable e) {
							EmiReloadLog.warn("Exception loading plugin provided by " + container.id(), e);
							if (restart) {
								continue outer;
							}
							continue;
						}
						EmiLog.info("Reloaded plugin from " + container.id() + " in " + (System.currentTimeMillis() - start) + "ms");
						if (restart) {
							continue outer;
						}
					}
					if (restart) {
						continue;
					}
					step(EmiPort.literal("Baking index"));
					EmiStackList.bake();
					step(EmiPort.literal("Registering late recipes"), 10_000);
					Consumer<EmiRecipe> registerLateRecipe = registry::addRecipe;
					for (Consumer<Consumer<EmiRecipe>> consumer : EmiRecipes.lateRecipes) {
						try {
							consumer.accept(registerLateRecipe);
						} catch (Exception e) {
							EmiReloadLog.warn("Exception loading late recipes for plugins:", e);
							if (restart) {
								continue outer;
							}
						}
					}
					step(EmiPort.literal("Baking recipes"), 15_000);
					EmiRecipes.bake();
					BoM.reload();
					EmiPersistentData.load();
					step(EmiPort.literal("Baking search"), 15_000);
					EmiSearch.bake();
					step(EmiPort.literal("Finishing up"));
					EmiScreenManager.search.update();
					EmiScreenManager.forceRecalculate();
					EmiReloadLog.bake();
					EmiLog.info("Reloaded EMI in " + (System.currentTimeMillis() - reloadStart) + "ms");
					// [WEI fork] diag#3
					EmiForkDiag.log(3, "EMI reload finished in " + (System.currentTimeMillis() - reloadStart) + "ms (restart=" + restart + ")");
					status = 2;
				} catch (Throwable e) {
					EmiReloadLog.warn("Critical error occured during reload:", e);
					status = -1;
					if (retries-- > 0) {
						restart = true;
					}
				}
			} while (restart);
			thread = null;
		}

		private final static int entrypointPriority(EmiPluginContainer container) {
			return container.id().equals("emi") ? 0 : 1;
		}
	}
}

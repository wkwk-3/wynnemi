package io.github.wkwk.wei;

import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** diag#10: 起動時に WEI・EMI・Wynntils の版を記録する。 */
final class StartupDiag {
    private StartupDiag() {}

    static void logEnvironment() {
        if (!Diag.enabled()) return;
        try {
            FabricLoader loader = FabricLoader.getInstance();
            Diag.log(10, "WEI version: {}", loader.getModContainer("wei")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
            Optional<ModContainer> emi = loader.getModContainer("emi");
            if (emi.isEmpty()) {
                Diag.warn(10, "No mod with id 'emi' is loaded");
                return;
            }
            ModContainer c = emi.get();
            Diag.log(10, "EMI loaded: name='{}' version={} provides={} origin={} containedIn={}",
                    c.getMetadata().getName(),
                    c.getMetadata().getVersion().getFriendlyString(),
                    c.getMetadata().getProvides(),
                    c.getOrigin(),
                    c.getContainingMod().map(m -> m.getMetadata().getId()).orElse("(top-level jar)"));
            Diag.log(10, "EMI (PF-EMI) loaded: {}, Wynntils version: {}",
                    loader.isModLoaded("emi"),
                    loader.getModContainer("wynntils").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("missing"));
        } catch (Throwable t) {
            Diag.warn(10, "Could not inspect loaded mods: {}", t.toString());
        }
    }
}

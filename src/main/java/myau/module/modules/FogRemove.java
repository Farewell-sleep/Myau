package myau.module.modules;

import myau.module.Module;

/**
 * FOG REMOVE — skidded from Onxy (FogRemoveModule).
 * Removes first-person environmental and distance fog. Applied by
 * MixinEntityRenderer at the end of setupFog.
 */
public class FogRemove extends Module {

    public FogRemove() {
        super("FogRemove", false);
    }
}

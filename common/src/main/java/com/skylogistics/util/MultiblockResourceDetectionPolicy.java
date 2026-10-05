package com.skylogistics.util;

import java.util.List;

/** Whitelists authorize recovery only for the named resource, after a real capability probe. */
public final class MultiblockResourceDetectionPolicy {
    public static final int ITEMS = 1;
    public static final int FLUIDS = 2;
    public static final int ENERGY = 4;
    public static final int ALL = ITEMS | FLUIDS | ENERGY;

    private MultiblockResourceDetectionPolicy() {
    }

    public static int mask(boolean items, boolean fluids, boolean energy) {
        return (items ? ITEMS : 0) | (fluids ? FLUIDS : 0) | (energy ? ENERGY : 0);
    }

    public static int recover(int enabled, int detected, int eligible) {
        return (enabled | (detected & eligible)) & ALL;
    }

    public static boolean validBlockId(Object value) {
        return value instanceof String text && text.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    public static List<String> defaultBlockWhitelist(int resource) {
        return allMultiblockBlocks().stream().filter(id -> (supportedResources(id) & resource) != 0).toList();
    }

    private static int supportedResources(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("induction")) return ITEMS | ENERGY;
        if (path.startsWith("dynamic_") || path.startsWith("thermal_evaporation")) return ITEMS | FLUIDS;
        if (path.startsWith("boiler_") || path.equals("superheating_element")
                || path.startsWith("fission_") || path.equals("control_rod_assembly")) return FLUIDS;
        if (path.startsWith("sps_") || path.equals("supercharged_coil")
                || path.startsWith("turbine_") || path.equals("rotational_complex")
                || path.equals("electromagnetic_coil") || path.equals("saturating_condenser")
                || path.equals("pressure_disperser")) return FLUIDS | ENERGY;
        // Fusion and shared structural blocks can belong to structures exposing all three resource groups.
        return ALL;
    }

    private static List<String> allMultiblockBlocks() {
        return List.of(
                "mekanism:dynamic_tank", "mekanism:dynamic_valve", "mekanism:structural_glass",
                "mekanism:induction_casing", "mekanism:induction_port",
                "mekanism:basic_induction_cell", "mekanism:advanced_induction_cell",
                "mekanism:elite_induction_cell", "mekanism:ultimate_induction_cell",
                "mekanism:basic_induction_provider", "mekanism:advanced_induction_provider",
                "mekanism:elite_induction_provider", "mekanism:ultimate_induction_provider",
                "mekanism:thermal_evaporation_block", "mekanism:thermal_evaporation_controller",
                "mekanism:thermal_evaporation_valve", "mekanism:boiler_casing", "mekanism:boiler_valve",
                "mekanism:superheating_element", "mekanism:pressure_disperser",
                "mekanism:sps_casing", "mekanism:sps_port", "mekanism:supercharged_coil",
                "mekanismgenerators:reactor_glass", "mekanismgenerators:fission_reactor_casing",
                "mekanismgenerators:fission_reactor_port", "mekanismgenerators:fission_reactor_logic_adapter",
                "mekanismgenerators:fission_fuel_assembly", "mekanismgenerators:control_rod_assembly",
                "mekanismgenerators:fusion_reactor_controller", "mekanismgenerators:fusion_reactor_frame",
                "mekanismgenerators:fusion_reactor_port", "mekanismgenerators:fusion_reactor_logic_adapter",
                "mekanismgenerators:laser_focus_matrix", "mekanismgenerators:turbine_casing",
                "mekanismgenerators:turbine_valve", "mekanismgenerators:turbine_vent",
                "mekanismgenerators:turbine_rotor",
                "mekanismgenerators:rotational_complex", "mekanismgenerators:electromagnetic_coil",
                "mekanismgenerators:saturating_condenser");
    }
}

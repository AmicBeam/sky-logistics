package com.skylogistics.compat.astages;

public record TransferRates(long items, long fluids, long chemicals, long energy, long mana, long source) {
    public TransferRates {
        if (items < 1L || fluids < 1L || chemicals < 1L || energy < 1L || mana < 1L || source < 1L) {
            throw new IllegalArgumentException("Transfer rates must be positive");
        }
    }

    public long get(TransferResource resource) {
        return switch (resource) {
            case ITEMS -> items;
            case FLUIDS -> fluids;
            case CHEMICALS -> chemicals;
            case ENERGY -> energy;
            case MANA -> mana;
            case SOURCE -> source;
        };
    }

    public TransferRates max(StageTransferRates stage) {
        return new TransferRates(
                Math.max(items, stage.getOrDefault(TransferResource.ITEMS, items)),
                Math.max(fluids, stage.getOrDefault(TransferResource.FLUIDS, fluids)),
                Math.max(chemicals, stage.getOrDefault(TransferResource.CHEMICALS, chemicals)),
                Math.max(energy, stage.getOrDefault(TransferResource.ENERGY, energy)),
                Math.max(mana, stage.getOrDefault(TransferResource.MANA, mana)),
                Math.max(source, stage.getOrDefault(TransferResource.SOURCE, source)));
    }
}

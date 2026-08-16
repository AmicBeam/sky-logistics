package com.skylogistics.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;

final class ArsNouveauApiBridge {
    private ArsNouveauApiBridge() {
    }

    static SourceHandlerBridge create(Object target) {
        return target instanceof ISourceTile sourceTile ? new Handler(sourceTile) : null;
    }

    private record Handler(ISourceTile sourceTile) implements SourceHandlerBridge {
        @Override
        public int getCurrentSource() {
            return Math.max(0, sourceTile.getSource());
        }

        @Override
        public int getMaxSource() {
            return Math.max(0, sourceTile.getMaxSource());
        }

        @Override
        public boolean canExtract() {
            return getCurrentSource() > 0;
        }

        @Override
        public boolean canReceive() {
            return sourceTile.canAcceptSource() && getCurrentSource() < getMaxSource();
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            int requested = Math.min(Math.max(0, amount), getCurrentSource());
            if (requested <= 0) {
                return 0;
            }
            if (simulate) {
                return requested;
            }
            int before = getCurrentSource();
            sourceTile.removeSource(requested);
            return Math.min(requested, Math.max(0, before - getCurrentSource()));
        }

        @Override
        public int insertSource(int amount, boolean simulate) {
            int requested = Math.min(Math.max(0, amount), Math.max(0, getMaxSource() - getCurrentSource()));
            if (requested <= 0 || !sourceTile.canAcceptSource()) {
                return 0;
            }
            if (simulate) {
                return requested;
            }
            int before = getCurrentSource();
            sourceTile.addSource(requested);
            return Math.min(requested, Math.max(0, getCurrentSource() - before));
        }
    }
}

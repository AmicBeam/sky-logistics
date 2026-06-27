package com.skylogistics.client;

public final class VaultTerminalViewState {
    private static final State ITEM_VAULT = new State();
    private static final State FLUID_VAULT = new State();

    private VaultTerminalViewState() {
    }

    public static State itemVault() {
        return ITEM_VAULT;
    }

    public static State fluidVault() {
        return FLUID_VAULT;
    }

    public static final class State {
        private String query = "";
        private int sortModeOrdinal;
        private int scrollRow;

        public String query() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query == null ? "" : query;
        }

        public int sortModeOrdinal() {
            return sortModeOrdinal;
        }

        public void setSortModeOrdinal(int sortModeOrdinal) {
            this.sortModeOrdinal = Math.max(0, sortModeOrdinal);
        }

        public int scrollRow() {
            return scrollRow;
        }

        public void setScrollRow(int scrollRow) {
            this.scrollRow = Math.max(0, scrollRow);
        }
    }
}

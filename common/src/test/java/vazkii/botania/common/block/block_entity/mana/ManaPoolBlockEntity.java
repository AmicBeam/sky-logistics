package vazkii.botania.common.block.block_entity.mana;

/** Minimal test fixture for Botania's reflective mana-pool contract. */
public final class ManaPoolBlockEntity {
    private final PoolState state;
    private int mana;

    private ManaPoolBlockEntity(Object block, int mana) {
        this.state = new PoolState(block);
        this.mana = mana;
    }

    public static ManaPoolBlockEntity modern(boolean creative, int mana) {
        return new ManaPoolBlockEntity(new ModernPoolBlock(creative), mana);
    }

    public static ManaPoolBlockEntity legacy(boolean creative, int mana) {
        return new ManaPoolBlockEntity(new LegacyPoolBlock(creative ? Variant.CREATIVE : Variant.DEFAULT), mana);
    }

    public int getCurrentMana() {
        return isCreative() ? getMaxMana() : mana;
    }

    public int getMaxMana() {
        return 1_000_000;
    }

    public boolean isFull() {
        return getCurrentMana() >= getMaxMana();
    }

    public void receiveMana(int amount) {
        if (!isCreative()) {
            mana = Math.max(0, Math.min(getMaxMana(), mana + amount));
        }
    }

    public PoolState getBlockState() {
        return state;
    }

    private boolean isCreative() {
        Object block = state.getBlock();
        return block instanceof ModernPoolBlock modern ? modern.isCreative()
                : ((LegacyPoolBlock) block).variant == Variant.CREATIVE;
    }

    public record PoolState(Object block) {
        public Object getBlock() {
            return block;
        }
    }

    public record ModernPoolBlock(boolean creative) {
        public boolean isCreative() {
            return creative;
        }
    }

    public static final class LegacyPoolBlock {
        public final Variant variant;

        private LegacyPoolBlock(Variant variant) {
            this.variant = variant;
        }
    }

    public enum Variant {
        DEFAULT,
        CREATIVE
    }
}

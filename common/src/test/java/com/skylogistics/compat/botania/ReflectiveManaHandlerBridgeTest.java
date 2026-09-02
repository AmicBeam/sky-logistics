package com.skylogistics.compat.botania;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

class ReflectiveManaHandlerBridgeTest {
    @Test
    void extractsFromModernCreativeManaPoolWhoseReportedManaDoesNotDecrease() {
        ManaPoolBlockEntity receiver = ManaPoolBlockEntity.modern(true, 1_000_000);
        ManaHandlerBridge bridge = ReflectiveManaHandlerBridge.create(receiver, null);

        assertEquals(50, bridge.extractMana(50, true));
        assertEquals(50, bridge.extractMana(50, false));
        assertEquals(1_000_000, bridge.getCurrentMana());
    }

    @Test
    void extractsFromLegacyCreativeManaPoolVariant() {
        ManaPoolBlockEntity receiver = ManaPoolBlockEntity.legacy(true, 1_000_000);
        ManaHandlerBridge bridge = ReflectiveManaHandlerBridge.create(receiver, null);

        assertEquals(75, bridge.extractMana(75, false));
        assertEquals(1_000_000, bridge.getCurrentMana());
    }

    @Test
    void keepsBeforeAfterValidationForNormalManaPools() {
        ManaPoolBlockEntity receiver = ManaPoolBlockEntity.modern(false, 200);
        ManaHandlerBridge bridge = ReflectiveManaHandlerBridge.create(receiver, null);

        assertEquals(80, bridge.extractMana(80, false));
        assertEquals(120, bridge.getCurrentMana());
    }

    @Test
    void doesNotTrustUnrelatedInfiniteManaReceivers() {
        InfiniteManaReceiver receiver = new InfiniteManaReceiver();
        ManaHandlerBridge bridge = ReflectiveManaHandlerBridge.create(receiver, null);

        assertEquals(100, bridge.extractMana(100, true));
        assertEquals(0, bridge.extractMana(100, false));
    }

    private static final class InfiniteManaReceiver {
        public int getCurrentMana() {
            return 1_000_000;
        }

        public int getMaxMana() {
            return 1_000_000;
        }

        public boolean isFull() {
            return true;
        }

        public void receiveMana(int amount) {
        }

        public FakeState getBlockState() {
            return new FakeState();
        }
    }

    private static final class FakeState {
        public FakeCreativeBlock getBlock() {
            return new FakeCreativeBlock();
        }
    }

    private static final class FakeCreativeBlock {
        public boolean isCreative() {
            return true;
        }
    }
}

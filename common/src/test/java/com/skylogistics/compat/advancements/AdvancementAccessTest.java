package com.skylogistics.compat.advancements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AdvancementAccessTest {
    @Test
    void supportsForge1201SrgNames() throws Exception {
        Advancement advancement = new Advancement();
        SrgManager manager = new SrgManager(advancement);
        SrgPlayerAdvancements playerAdvancements = new SrgPlayerAdvancements();

        assertAdvancementAccess(manager, playerAdvancements, advancement);
    }

    @Test
    void supportsMojmapNames() throws Exception {
        Advancement advancement = new Advancement();
        MojmapManager manager = new MojmapManager(advancement);
        MojmapPlayerAdvancements playerAdvancements = new MojmapPlayerAdvancements();

        assertAdvancementAccess(manager, playerAdvancements, advancement);
    }

    private static void assertAdvancementAccess(Object manager, Object playerAdvancements, Advancement expected)
            throws Exception {
        Method lookup = AdvancementAccess.findLookup(manager);
        assertNotNull(lookup);
        Object advancement = AdvancementAccess.findAdvancement(manager, lookup, "minecraft:story/smelt_iron");
        assertSame(expected, advancement);

        assertFalse(AdvancementAccess.isDone(AdvancementAccess.progress(playerAdvancements, advancement)));
        AdvancementAccess.setAwarded(playerAdvancements, advancement, true);
        assertTrue(AdvancementAccess.isDone(AdvancementAccess.progress(playerAdvancements, advancement)));
        AdvancementAccess.setAwarded(playerAdvancements, advancement, false);
        assertFalse(AdvancementAccess.isDone(AdvancementAccess.progress(playerAdvancements, advancement)));
    }

    public static final class ResourceLocation {
        public ResourceLocation(String value) {
        }
    }

    public static final class Advancement {
    }

    public static final class SrgProgress {
        private boolean done;

        public boolean m_8193_() {
            return done;
        }
    }

    public static final class MojmapProgress {
        private boolean done;

        public boolean isDone() {
            return done;
        }
    }

    public static final class SrgManager {
        private final Advancement advancement;

        public SrgManager(Advancement advancement) {
            this.advancement = advancement;
        }

        public Advancement m_136041_(ResourceLocation id) {
            return advancement;
        }
    }

    public static final class MojmapManager {
        private final Advancement advancement;

        public MojmapManager(Advancement advancement) {
            this.advancement = advancement;
        }

        public Advancement getAdvancement(ResourceLocation id) {
            return advancement;
        }
    }

    public static final class SrgPlayerAdvancements {
        private final SrgProgress progress = new SrgProgress();

        public SrgProgress m_135996_(Advancement advancement) {
            return progress;
        }

        public boolean m_135988_(Advancement advancement, String criterion) {
            progress.done = true;
            return true;
        }

        public boolean m_135998_(Advancement advancement, String criterion) {
            progress.done = false;
            return true;
        }
    }

    public static final class MojmapPlayerAdvancements {
        private final MojmapProgress progress = new MojmapProgress();

        public MojmapProgress getOrStartProgress(Advancement advancement) {
            return progress;
        }

        public boolean award(Advancement advancement, String criterion) {
            progress.done = true;
            return true;
        }

        public boolean revoke(Advancement advancement, String criterion) {
            progress.done = false;
            return true;
        }
    }
}

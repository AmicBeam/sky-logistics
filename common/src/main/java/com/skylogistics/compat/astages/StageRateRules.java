package com.skylogistics.compat.astages;

import java.util.Map;
import java.util.Set;

public record StageRateRules(TransferRates initial, Map<String, StageTransferRates> stages) {
    public StageRateRules {
        stages = Map.copyOf(stages);
    }

    public boolean hasUnlocks() {
        return !stages.isEmpty();
    }

    public TransferRates ratesFor(Set<String> ownedStages) {
        TransferRates result = initial;
        for (String stage : ownedStages) {
            StageTransferRates unlocked = stages.get(stage);
            if (unlocked != null) {
                result = result.max(unlocked);
            }
        }
        return result;
    }
}

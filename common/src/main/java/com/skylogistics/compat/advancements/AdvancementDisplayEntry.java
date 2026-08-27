package com.skylogistics.compat.advancements;

import com.skylogistics.compat.astages.TransferRates;

/** Ordered, configuration-owned display data for one generated advancement node. */
public record AdvancementDisplayEntry(String advancement, String icon, String title, String frame,
        TransferRates rates) {
}

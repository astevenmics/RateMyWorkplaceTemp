package com.ratemyworkplace.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** Functional departments a {@link Location} can be tagged with, for filtering and display. */
public enum Department {
    IT,
    MEDICAL,
    ADMIN,
    SALES,
    MARKETING,
    HR,
    FINANCE,
    OPERATIONS,
    CUSTOMER_SERVICE,
    ENGINEERING,
    RETAIL,
    LOGISTICS,
    LEGAL,
    OTHER;

    /** Title-cased label for display (e.g. CUSTOMER_SERVICE -> "Customer Service"). */
    public String label() {
        String[] words = name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static final int MAX_LENGTH = 60;

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String key = trimmed.toUpperCase().replace(' ', '_');
        for (Department d : values()) {
            if (d.name().equals(key)) {
                return d.label();
            }
        }
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }

    public static Set<String> normalizeSet(Set<String> raw, int maxCount) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        if (raw == null) {
            return result;
        }
        for (String s : raw) {
            String normalized = normalize(s);
            if (normalized == null || result.size() >= maxCount) {
                continue;
            }
            String dedupeKey = normalized.toLowerCase();
            if (seen.add(dedupeKey)) {
                result.add(normalized);
            }
        }
        return result;
    }
}
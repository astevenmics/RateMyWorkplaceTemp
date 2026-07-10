package com.ratemyworkplace.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The predefined, well-known departments offered as quick-pick options wherever a
 * location's departments are chosen. Storage itself ({@link Location#getDepartments()},
 * {@link Feedback#getDepartments()}) is free-text ({@code Set<String>}) so a custom
 * department/position that isn't in this list can still be recorded — this enum only
 * supplies the canonical label for anything that happens to match one of these.
 */
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

    /**
     * Normalises one department/position value for storage: matches against the
     * predefined list (case/spacing-insensitive) to reuse its canonical label, or
     * otherwise keeps the caller's own free text (trimmed, length-capped) as a custom
     * department/position.
     */
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

    /** Normalises a whole set, dropping blanks and case-insensitive duplicates, capped at {@code maxCount} entries. */
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

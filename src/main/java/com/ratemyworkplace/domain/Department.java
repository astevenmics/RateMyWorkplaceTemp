package com.ratemyworkplace.domain;

import java.util.EnumSet;
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

    public static Set<Department> parseSet(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EnumSet.noneOf(Department.class);
        }
        Set<Department> departments = EnumSet.noneOf(Department.class);
        for (String name : raw) {
            if (name != null && !name.isBlank()) {
                departments.add(Department.valueOf(name.trim().toUpperCase().replace(' ', '_')));
            }
        }
        return departments;
    }
}

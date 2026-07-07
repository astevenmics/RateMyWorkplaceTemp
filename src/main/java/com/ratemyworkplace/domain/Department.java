package com.ratemyworkplace.domain;

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
}

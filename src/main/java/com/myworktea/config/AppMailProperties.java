package com.myworktea.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    /** When false, verification codes are logged instead of e-mailed (useful for local dev). */
    private boolean enabled = false;
    private String from = "no-reply@myworktea.local";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}
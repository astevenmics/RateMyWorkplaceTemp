package com.ratemyworkplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private long capacity = 120;
    private long refillTokens = 120;
    private long refillPeriodSeconds = 60;
    private long sensitiveCapacity = 15;
    private long sensitiveRefillTokens = 15;
    private long sensitiveRefillPeriodSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }
    public long getRefillTokens() { return refillTokens; }
    public void setRefillTokens(long refillTokens) { this.refillTokens = refillTokens; }
    public long getRefillPeriodSeconds() { return refillPeriodSeconds; }
    public void setRefillPeriodSeconds(long refillPeriodSeconds) { this.refillPeriodSeconds = refillPeriodSeconds; }
    public long getSensitiveCapacity() { return sensitiveCapacity; }
    public void setSensitiveCapacity(long sensitiveCapacity) { this.sensitiveCapacity = sensitiveCapacity; }
    public long getSensitiveRefillTokens() { return sensitiveRefillTokens; }
    public void setSensitiveRefillTokens(long t) { this.sensitiveRefillTokens = t; }
    public long getSensitiveRefillPeriodSeconds() { return sensitiveRefillPeriodSeconds; }
    public void setSensitiveRefillPeriodSeconds(long s) { this.sensitiveRefillPeriodSeconds = s; }
}

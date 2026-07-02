package com.ratemyworkplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private String dir = "./uploads";
    private List<String> allowedContentTypes = List.of("application/pdf", "image/png", "image/jpeg");

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> allowedContentTypes) { this.allowedContentTypes = allowedContentTypes; }
}
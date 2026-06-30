package com.ratemyworkplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the RateMyWorkplace platform.
 *
 * <p>{@link EnableSpringDataWebSupport} wires Spring Data's {@code Pageable} /
 * {@code Sort} argument resolvers and serializes {@code Page} responses through a
 * stable DTO so the JavaScript frontend always receives the same pagination shape.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class RateMyWorkplaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateMyWorkplaceApplication.class, args);
    }
}

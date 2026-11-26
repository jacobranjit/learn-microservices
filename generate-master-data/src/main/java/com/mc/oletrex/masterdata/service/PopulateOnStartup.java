package com.mc.oletrex.masterdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Populates once the service has started, when masterdata.populate-on-startup=true. */
@Component
@ConditionalOnBooleanProperty("masterdata.populate-on-startup")
public class PopulateOnStartup {

    private static final Logger log = LoggerFactory.getLogger(PopulateOnStartup.class);

    private final MasterDataService masterDataService;

    public PopulateOnStartup(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void populate() {
        log.info("masterdata.populate-on-startup=true: populating master data");
        try {
            var report = masterDataService.populate();
            if (!report.success()) {
                report.errors().forEach(e -> log.warn("Failed {} {}: {}", e.step(), e.key(), e.message()));
            }
        } catch (RuntimeException e) {
            // Keep the service running so populate can be retried through the API.
            log.error("Populating master data on startup failed: {}", e.getMessage());
        }
    }
}

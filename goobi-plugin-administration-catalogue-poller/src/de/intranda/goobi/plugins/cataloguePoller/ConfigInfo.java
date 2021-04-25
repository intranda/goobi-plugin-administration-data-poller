package de.intranda.goobi.plugins.cataloguePoller;

import lombok.Data;

@Data
public class ConfigInfo {
    private String title;
    private String filter;
    private String catalogue;
    private String catalogueIdentifier;
    private boolean mergeRecords;
    private String skipFields;
    private boolean analyseSubElements;
    private String startTime;
    private String delay;
    private boolean exportUpdatedRecords;
    private String lastRun;
}
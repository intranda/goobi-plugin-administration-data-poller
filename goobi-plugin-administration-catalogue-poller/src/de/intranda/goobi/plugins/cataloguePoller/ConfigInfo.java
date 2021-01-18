package de.intranda.goobi.plugins.cataloguePoller;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConfigInfo {

    private String title;
    private String filter;
    private String configCatalogue;
    private String configCatalogueId;
    private String configMergeRecords;
    private String configSkipFields;

    private String lastRun;


}

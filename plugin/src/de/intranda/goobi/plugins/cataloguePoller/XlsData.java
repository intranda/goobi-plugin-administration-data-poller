package de.intranda.goobi.plugins.cataloguePoller;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class XlsData {
    private String field;
    private String oldValues;
    private String newValues;
} 
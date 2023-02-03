package de.intranda.goobi.plugins.datapoller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.production.cli.helper.StringPair;

import de.intranda.goobi.plugins.DataPollerPlugin;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConfigHelper {
    private XMLConfiguration config;
    private static final DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private String pluginName;

    public ConfigHelper() {
        this.pluginName = DataPollerPlugin.getPluginName();
        this.config = ConfigPlugins.getPluginConfig(this.pluginName);
        this.config.setExpressionEngine(new XPathExpressionEngine());
    }

    public HashMap<String, ConfigInfo> readConfigInfo() {
        HashMap<String, ConfigInfo> map = new HashMap<>();
        // run through all rules
        List<HierarchicalConfiguration> rulelist = this.config.configurationsAt("rule");
        for (HierarchicalConfiguration rule : rulelist) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(rule.getLong("lastRun", 0));

            ConfigInfo ci = new ConfigInfo();
            ci.setTitle(rule.getString("@title"));
            ci.setFilter(rule.getString("filter"));
            ci.setCatalogue(rule.getString("catalogue"));

            List<StringPair> searchfields = new ArrayList<>();
            List<HierarchicalConfiguration> fields = rule.configurationsAt("catalogueField");
            for (HierarchicalConfiguration field : fields) {
                String fieldname = field.getString("@fieldName");
                String metadataName = field.getString("@fieldValue");
                searchfields.add(new StringPair(fieldname, metadataName));
            }
            ci.setSearchFields(searchfields);

            //FileHandling
            ci.setRuleType(rule.getString("@type"));
            ci.setPath(rule.getString("path", ""));
            ci.setWorkflow(rule.getString("workflow", null));
            ci.setPublicationType(rule.getString("publicationType", null));

            ci.setFileHandlingFileFilter(rule.getString("fileHandling/@fileFilter", "*\\.xml"));

            ci.setCreateMissingProcesses(rule.getBoolean("createMissingProcesses", false));
            ci.setMergeRecords(rule.getBoolean("mergeRecords"));
            ci.setFieldListMode(rule.getString("fieldList/@mode", null));
            ci.setFieldFilterList(Arrays.asList(rule.getStringArray("fieldList/field")));

            ci.setExportUpdatedRecords(rule.getBoolean("exportUpdatedRecords", false));
            ci.setAnalyseSubElements(rule.getBoolean("analyseSubElements"));

            //quartz job related attributes
            ci.setStartTime(rule.getString("@startTime"));
            ci.setDelay(rule.getInt("@delay", 24));
            ci.setJobActive(rule.getBoolean("@activateJob", false));

            ci.setLastRun(formatter.format(calendar.getTime()));

            map.put(ci.getTitle(), ci);
        }
        return map;
    }

    public void updateLastRun(String ruleName, long lastRunMillis) {
        try {
            HierarchicalConfiguration rule = this.config.configurationAt("rule[@title='" + ruleName + "']");
            rule.setProperty("lastRun", lastRunMillis);
            Path configurationFile = Paths.get(ConfigurationHelper.getInstance().getConfigurationFolder(), "plugin_" + this.pluginName + ".xml");
            this.config.save(configurationFile.toString());
        } catch (ConfigurationException e) {
            log.error("Error while updating the configuration file", e);
        }

    }
}

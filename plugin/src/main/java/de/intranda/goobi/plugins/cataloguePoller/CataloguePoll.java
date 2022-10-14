/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */
package de.intranda.goobi.plugins.cataloguePoller;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.jms.JMSException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.api.mq.QueueType;
import org.goobi.api.mq.TaskTicket;
import org.goobi.api.mq.TicketGenerator;
import org.goobi.api.mq.ticket.ConfigInfo;
import org.goobi.api.mq.ticket.XlsFileManager;
import org.goobi.api.mq.ticket.PollDocStruct.PullDiff;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.omnifaces.util.Faces;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
public class CataloguePoll {
    private XMLConfiguration config;
    private List<PullDiff> differences;
    private boolean testRun;
    private HashMap<String, Path> xlsxReports = new HashMap<>();
    private List<ConfigInfo> ci;

    private static final DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public CataloguePoll() {
        config = ConfigPlugins.getPluginConfig("intranda_administration_catalogue_poller");
        config.setExpressionEngine(new XPathExpressionEngine());
    }

    public void executeAll() {

        List<HierarchicalConfiguration> rules = config.configurationsAt("rule");

        for (HierarchicalConfiguration rule : rules) {
            execute(rule.getString("@title"));
        }
    }

    public void executeTest(String ruleName) {
        executePoll(ruleName, true);
    }

    public void execute(String ruleName) {
        executePoll(ruleName, false);
    }

    public void download(String ruleName) {
        Path report = xlsxReports.get(ruleName);
        if (report != null) {
            try {
                Faces.sendFile(report.toFile(), true);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean reportExists(String ruleName) {
        if (this.xlsxReports.isEmpty()) {
            this.xlsxReports = XlsFileManager.manageTempFiles(ConfigurationHelper.getInstance().getTemporaryFolder(), ci);
        }
        return xlsxReports.containsKey(ruleName);
    }

    /**
     * do the pull of catalogue data to update the records for all rules
     */
    public void executePoll(String ruleName, boolean testRun) {
        this.testRun = testRun;
        log.debug(" Starting to update the METS files fo all processes defined in the rule ");
        differences = new ArrayList<>();

        // run through all rules
        HierarchicalConfiguration rule = config.configurationAt("rule[@title='" + ruleName + "']");
        // first get all parameters of the rule
        String title = rule.getString("@title");
        String filter = rule.getString("filter");
        String configCatalogue = rule.getString("catalogue");

        List<StringPair> searchfields = new ArrayList<>();
        List<HierarchicalConfiguration> fields = rule.configurationsAt("catalogueField");
        for (HierarchicalConfiguration field : fields) {
            String fieldname = field.getString("@fieldName");
            String metadataName = field.getString("@fieldValue");
            searchfields.add(new StringPair(fieldname, metadataName));
        }

        boolean configMergeRecords = rule.getBoolean("mergeRecords");
        boolean configAnalyseSubElements = rule.getBoolean("analyseSubElements");
        boolean exportUpdatedRecords = rule.getBoolean("exportUpdatedRecords", false);
        String configListType = rule.getString("fieldList/@mode", null);
        boolean isBlockList = false;

        List<String> fieldFilterList = Arrays.asList(rule.getStringArray("fieldList/field"));

        if (!fieldFilterList.isEmpty()) {
            if (configListType == null) {
                Helper.setFehlerMeldung("plugin_admin_cataloguePoller_configErrorModeMissing");
                log.error("The mode Attribut of the fieldList element ist not specified! Pleas update the configuration file!");
                return;
            }

            switch (configListType) {
                case "blacklist":
                    isBlockList = true;
                    break;
                case "whitelist":
                    isBlockList = false;
                    break;
                default:
                    Helper.setFehlerMeldung("plugin_admin_cataloguePoller_configErrorModeInvalid");
                    log.error("CatloguePollerPlugin: The value of the attribute mode: " + configListType
                            + " is invalid! Pleas update the configuration file!");
                    return;
            }
        } else {
            if (configListType.equals("whitelist")) {
                Helper.setFehlerMeldung("plugin_admin_cataloguePoller_configErrorEmptyWhiteList");
                log.error("CatloguePollerPlugin: The filterlist is a whitelist but has no elements!");
                return;
            }
            // if no list is specified run as if a black list with no Elements was given
            if (configListType.equals("blacklist")) {
                isBlockList = true;
            }
        }

        log.debug("Rule '" + title + "' with filter '" + filter + "'");

        // now filter the list of all processes that should be affected and
        // fun through it
        String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);

        List<Integer> processIds = ProcessManager.getIdsForFilter(query);
        for (Integer id : processIds) {
            // create a new ticket
            TaskTicket ticket = TicketGenerator.generateSimpleTicket("CatalogueRequest");
            ticket.setProcessId(id);

            // add rule configuration to ticket
            ticket.getProperties().put("mergeRecords", String.valueOf(configMergeRecords));
            ticket.getProperties().put("analyseSubElements", String.valueOf(configAnalyseSubElements));
            ticket.getProperties().put("exportUpdatedRecords", String.valueOf(exportUpdatedRecords));
            ticket.getProperties().put("catalogueName", configCatalogue);
            ticket.getProperties().put("testRun", String.valueOf(testRun));
            ticket.getProperties().put("blockList", String.valueOf(isBlockList));

            StringBuilder sb = new StringBuilder();
            for (StringPair field : searchfields) {
                if (sb.length() > 0) {
                    sb.append("|");
                }
                sb.append(field.getOne());
                sb.append("=");
                sb.append(field.getTwo());
            }
            ticket.getProperties().put("searchfields", sb.toString());
            sb = new StringBuilder();
            for (String field : fieldFilterList) {
                if (sb.length() > 0) {
                    sb.append("|");
                }
                sb.append(field);
            }
            ticket.getProperties().put("fieldFilter", sb.toString());

            // submit ticket
            try {
                TicketGenerator.submitInternalTicket(ticket, QueueType.SLOW_QUEUE, "CatalogueRequest", id);
            } catch (JMSException e) {
                log.error(e);
            }
        }
        long lastRunMillis = System.currentTimeMillis();
        // write last updated time into the configuration file
        try {
            rule.setProperty("lastRun", lastRunMillis);
            Path configurationFile =
                    Paths.get(ConfigurationHelper.getInstance().getConfigurationFolder(), "plugin_intranda_administration_catalogue_poller.xml");
            config.save(configurationFile.toString());
        } catch (ConfigurationException e) {
            log.error("Error while updating the configuration file", e);
        }
    }

    /**
     * get the list of all configurations to show it in the GUI
     * 
     * @return
     */
    public List<ConfigInfo> getConfigInfo() {

        List<ConfigInfo> list = new ArrayList<>();
        // run through all rules
        List<HierarchicalConfiguration> rulelist = config.configurationsAt("rule");
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

            ci.setMergeRecords(rule.getBoolean("mergeRecords"));
            ci.setFieldListMode(rule.getString("fieldList/@mode"));
            ci.setFilterList(String.valueOf(rule.getList("fieldList/field")));

            ci.setExportUpdatedRecords(rule.getBoolean("exportUpdatedRecords"));
            ci.setAnalyseSubElements(rule.getBoolean("analyseSubElements"));
            ci.setStartTime(rule.getString("@startTime"));
            ci.setDelay(rule.getString("@delay"));

            ci.setLastRun(formatter.format(calendar.getTime()));

            list.add(ci);
        }
        this.ci = list;
        return list;
    }

}

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
package de.intranda.goobi.plugins.datapoller;

import de.intranda.goobi.plugins.datapoller.xls.FileManager;
import de.intranda.goobi.plugins.datapoller.xls.FolderInfo;
import de.intranda.goobi.plugins.datapoller.xls.ReportInfo;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.persistence.managers.ProcessManager;
import jakarta.jms.JMSException;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.api.mq.QueueType;
import org.goobi.api.mq.TaskTicket;
import org.goobi.api.mq.TicketGenerator;
import org.goobi.managedbeans.MessageQueueBean;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.omnifaces.util.Faces;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Log4j2
public class DataPoll {
    private XMLConfiguration config;
    private ConfigHelper cHelper;
    private List<PullDiff> differences;
    private boolean ticketStateTestRun;
    private HashMap<String, Path> xlsxReports = new HashMap<>();
    private HashMap<String, ConfigInfo> ci = new HashMap<>();
    private boolean ticketsActive = false;
    private boolean allowRun = false;
    private boolean ticketStateUnfinished = true;
    private boolean queueIsUp = false;
    private boolean quartzJob;

    public DataPoll(boolean quartzJob) {

        this.cHelper = new ConfigHelper();
        this.quartzJob = quartzJob;
        //this should be moved

        if (!this.quartzJob) {
            MessageQueueBean queueBean = Helper.getBeanByClass(MessageQueueBean.class);
            if (queueBean.isMessageBrokerStart()) {
                this.queueIsUp = true;
                Map<String, Integer> activeTicketType = queueBean.getSlowQueueContent();
                if (activeTicketType.containsKey("CatalogueRequest")) {
                    this.ticketsActive = true;
                }
            } else {
                // Helper.setFehlerMeldung("The Message Queue is not activated");
                log.debug("The Message Queue is not activated!");
            }
            this.allowRun = this.queueIsUp && !this.ticketsActive;
        } else {
            // if this run was trigered by a quartz job only check if queue is running.
            this.allowRun = ConfigurationHelper.getInstance().isStartInternalMessageBroker();
            if (!allowRun) {
                log.debug("The Message Queue is not activated!");
            }
        }
    }

    public void executeAll() {

        List<HierarchicalConfiguration> rules = this.config.configurationsAt("rule");

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
        Path report = this.xlsxReports.get(ruleName);
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
            HashMap<String, FolderInfo> infos =
                    FileManager.manageTempFiles(Paths.get(ConfigurationHelper.getInstance().getTemporaryFolder()), this.ci.values());
            Set<String> keys = infos.keySet();
            for (String key : keys) {
                FolderInfo info = infos.get(key);
                this.xlsxReports.put(key, info.getXlsFile());
                List<PullDiff> diffsForDisplay = info.getDifferences(0);
                ReportInfo rInfo = info.getInfo();
                if (!diffsForDisplay.isEmpty()) {
                    this.differences = diffsForDisplay;
                    if (rInfo != null) {
                        this.ticketStateUnfinished = (info.getDiffSize() < rInfo.getTicketCount() && this.ticketsActive);
                        this.ticketStateTestRun = rInfo.isTestRun();
                    }
                }

            }
        }
        return this.xlsxReports.containsKey(ruleName);
    }

    /**
     * do the pull of catalogue data to update the records for all rules
     */
    public void executePoll(String ruleName, boolean testRun) {
        if (!this.allowRun) {
            log.debug(
                    "DataPollerPlugin: Error starting Poll either the message queue wasn't activated or another catalogue poll job was in progress! ");
            return;
        } else {
            // set allowRun to false after starting a new poll
            this.allowRun = false;
        }
        log.debug("DataPollerPlugin: Starting to update the METS files fo all processes defined in the rule ");

        // first get all parameters of the rule
        if (this.ci.isEmpty()) {
            this.ci = this.cHelper.readConfigInfo();
        }

        ConfigInfo info = this.ci.get(ruleName);
        // run through all rules

        // make sure job will not run if isactive is false
        if (this.quartzJob && !info.isEnabled()) {
            log.debug("Quartz Job was triggered but rule was specified to be inactive (jobActive is false)");
            return;
        }
        if ("hotfolder".equals(info.getRuleType())) {
            if (!StorageProvider.getInstance().isFileExists(Paths.get(info.getPath()))) {
                Helper.setFehlerMeldung("plugin_admin_dataPoller_configErrorHotfolderMissing");
                log.error("DataPollerPlugin: The hotfolder {} does not exist!", info.getPath());

                return;
            }
        }

        boolean isBlockList = false;

        if (!info.getFieldFilterList().isEmpty()) {
            if (info.getFieldListMode() == null) {
                Helper.setFehlerMeldung("plugin_admin_dataPoller_configErrorModeMissing");
                log.error("The mode Attribut of the fieldList element ist not specified! Pleas update the configuration file!");
                return;
            }

            switch (info.getFieldListMode()) {
                case "blacklist":
                    isBlockList = true;
                    break;
                case "whitelist":
                    isBlockList = false;
                    break;
                default:
                    Helper.setFehlerMeldung("plugin_admin_dataPoller_configErrorModeInvalid");
                    log.error("DataPollerPlugin: The value of the attribute mode: " + info.getFieldListMode()
                            + " is invalid! Pleas update the configuration file!");
                    return;
            }
        } else {
            if ("whitelist".equals(info.getFieldListMode())) {
                Helper.setFehlerMeldung("plugin_admin_dataPoller_configErrorEmptyWhiteList");
                log.error("DataPollerPlugin: The filterlist is a whitelist but has no elements!");
                return;
            }
            // if no list is specified run as if a black list with no Elements was given
            if (info.getFieldListMode() == null || "blacklist".equals(info.getFieldListMode())) {
                isBlockList = true;
            }
        }

        log.debug("Rule '" + info.getTitle() + "' with filter '" + info.getFilter() + "'");

        // now filter the list of all processes that should be affected and
        // fun through it
        String query = FilterHelper.criteriaBuilder(info.getFilter(), false, null, null, null, true, false);

        List<Integer> processIds = ProcessManager.getIdsForFilter(query);
        long lastRunMillis = System.currentTimeMillis();
        String tempFolder = ConfigurationHelper.getInstance().getTemporaryFolder();
        StringBuilder xmlTempFolder = new StringBuilder();
        xmlTempFolder.append("catPoll")
                .append("_")
                .append(ruleName.toLowerCase().trim().replace(" ", "_"))
                .append("_")
                .append(lastRunMillis)
                .append("_")
                .append(processIds.size());
        Path xmlTempFolderPath = FileManager.createXmlFolder(tempFolder, xmlTempFolder.toString());
        //create reportInfoXml

        ReportInfo rinfo;
        if ("hotfolder".equals(info.getRuleType())) {
            List<Path> hotfolderFiles = FileManager.getHotfolderFiles(info.getPath(), info.getFileHandlingFileFilter());
            rinfo = new ReportInfo(testRun, ruleName, lastRunMillis, hotfolderFiles.size());
            ReportInfo.marshalReportInfo(rinfo, xmlTempFolderPath);
            int index = 0;
            for (Path hotfolderFile : hotfolderFiles) {
                TaskTicket ticket = TicketGenerator.generateSimpleTicket("CatalogueRequest");
                ticket.setProcessId(--index);
                ticket.getProperties().put("hotfolderFile", String.valueOf(hotfolderFile.toString()));
                ticket.getProperties().put("createMissingProcesses", String.valueOf(info.isCreateMissingProcesses()));
                ticket.getProperties().put("publicationType", info.getPublicationType());
                ticket.getProperties().put("workflow", info.getWorkflow());
                // add rule configuration to ticket and submit it
                updateAndSubmitTicket(ticket, info, testRun, isBlockList, lastRunMillis, xmlTempFolderPath);
            }
        } else {
            rinfo = new ReportInfo(testRun, ruleName, lastRunMillis, processIds.size());

            for (Integer id : processIds) {
                // create a new ticket
                TaskTicket ticket = TicketGenerator.generateSimpleTicket("CatalogueRequest");
                ticket.setProcessId(id);

                // add rule configuration to ticket and submit it
                updateAndSubmitTicket(ticket, info, testRun, isBlockList, lastRunMillis, xmlTempFolderPath);
            }
        }
        // write last updated time into the configuration file
        this.cHelper.updateLastRun(ruleName, lastRunMillis);
    }

    private void updateAndSubmitTicket(TaskTicket ticket, ConfigInfo info, boolean testRun, boolean isBlockList, long lastRunMillis,
            Path xmlTempFolderPath) {
        // add rule configuration to ticket
        ticket.getProperties().put("ruleType", String.valueOf(info.getRuleType()));
        ticket.getProperties().put("mergeRecords", String.valueOf(info.isMergeRecords()));
        ticket.getProperties().put("analyseSubElements", String.valueOf(info.isAnalyseSubElements()));
        ticket.getProperties().put("exportUpdatedRecords", String.valueOf(info.isExportUpdatedRecords()));
        ticket.getProperties().put("catalogueName", info.getCatalogue());
        ticket.getProperties().put("testRun", String.valueOf(testRun));
        ticket.getProperties().put("blockList", String.valueOf(isBlockList));
        ticket.getProperties().put("lastRunMillis", String.valueOf(lastRunMillis));
        ticket.getProperties().put("xmlTempFolder", xmlTempFolderPath.toString());

        StringBuilder sb = new StringBuilder();
        for (StringPair field : info.getSearchFields()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(field.getOne());
            sb.append("=");
            sb.append(field.getTwo());
        }
        ticket.getProperties().put("searchfields", sb.toString());

        sb = new StringBuilder();
        for (String field : info.getFieldFilterList()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(field);
        }
        ticket.getProperties().put("fieldFilter", sb.toString());

        sb = new StringBuilder();
        for (String field : info.getSteps()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(field);
        }
        ticket.getProperties().put("steps", sb.toString());

        // submit ticket
        try {
            TicketGenerator.submitInternalTicket(ticket, QueueType.SLOW_QUEUE, "CatalogueRequest", ticket.getProcessId());
        } catch (JMSException e) {
            log.error(e);
        }
    }

    /**
     * get the list of all configurations to show it in the GUI
     * 
     * @return
     */
    public Collection<ConfigInfo> getConfigInfo() {
        this.ci = this.cHelper.readConfigInfo();
        return this.ci.values();
    }

}

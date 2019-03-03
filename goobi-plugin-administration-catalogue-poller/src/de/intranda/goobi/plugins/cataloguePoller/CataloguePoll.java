package de.intranda.goobi.plugins.cataloguePoller;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Process;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IOpacPlugin;

import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;

@Data
@Log4j
public class CataloguePoll {
    private XMLConfiguration config;
    private List<PullDiff> differences;

    public CataloguePoll() {
        config = ConfigPlugins.getPluginConfig("intranda_administration_catalogue_poller");
        config.setExpressionEngine(new XPathExpressionEngine());
    }

    /**
     * do the pull of catalogue data to update the records for all rules
     */
    public void execute() {
        log.debug(" Starting to update the METS files fo all processes defined in the rules ");
        differences = new ArrayList<>();

        // run through all rules
        List<HierarchicalConfiguration> rulelist = config.configurationsAt("rule");
        for (HierarchicalConfiguration rule : rulelist) {
            // first get all parameters of the rule
            String title = rule.getString("@title");
            String filter = rule.getString("filter");
            String configCatalogue = rule.getString("catalogue");
            String configCatalogueId = rule.getString("catalogueIdentifier");
            boolean configMergeRecords = rule.getBoolean("mergeRecords");
            List<String> configSkipFields = rule.getList("skipField");
            log.debug("Rule '" + title + "' with filter '" + filter + "'");

            // now filter the list of all processes that should be affected and
            // fun through it
            String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);
            List<Process> processes = ProcessManager.getProcesses("prozesse.titel", query);
            for (Process process : processes) {
                updateMetsFileForProcess(process, configCatalogue, configCatalogueId, configMergeRecords, configSkipFields);
            }
        }

        // write last updated time into the configuration file
        try {
            config.setProperty("lastRun", System.currentTimeMillis());
            config.save();
        } catch (ConfigurationException e) {
            log.error("Error while updating the configuration file", e);
        }
    }

    /**
     * Method to update the mets file of a process with new data
     * 
     * @param p
     * @param configCatalogue the catalogue name to use (e.g. GBV, Wiener, ...)
     * @param configCatalogueId the identifierfield to use (e.g. $(meta.CatalogIDDigital))
     * @param configMergeRecords define if the content shall be merged (true) or overwritten (false)
     * @param configSkipFields define a list of fields that shall now be updated during merging (e.g. CatalogueIDDigital, DocLanguage ...)
     * @return
     */
    public boolean updateMetsFileForProcess(Process p, String configCatalogue, String configCatalogueId, boolean configMergeRecords,
            List<String> configSkipFields) {
        log.debug("Starting catalogue request using catalogue: " + configCatalogue + " with identifier field " + configCatalogueId);

        // first read the original METS file for the process
        Fileformat ffOld = null;
        DigitalDocument dd = null;
        Prefs prefs = p.getRegelsatz().getPreferences();
        DocStruct topstructOld = null;
        DocStruct anchorOld = null;
        DocStruct physOld = null;

        try {
            ffOld = p.readMetadataFile();
            if (ffOld == null) {
                log.error("Metadata file is not readable for process with ID " + p.getId());
                Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, "Metadata file is not readable for catalogue poller plugin");
                return false;
            }
            dd = ffOld.getDigitalDocument();
            topstructOld = dd.getLogicalDocStruct();
            if (topstructOld.getType().isAnchor()) {
                anchorOld = topstructOld;
                topstructOld = topstructOld.getAllChildren().get(0);
            }
            physOld = dd.getPhysicalDocStruct();
        } catch (Exception e) {
            log.error("Exception occurred while reading the metadata file for process with ID " + p.getId(), e);
            Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, "Exception occurred in catalogue poller plugin while reading the metadata file: "
                    + e.getMessage());
            return false;
        }

        // create a VariableReplacer to transform the identifier field from the
        // configuration into a real value
        VariableReplacer replacer = new VariableReplacer(dd, prefs, p, null);
        String catalogueId = replacer.replace(configCatalogueId);
        log.debug("Using this value for the catalogue request: " + catalogueId);

        // request the wished catalogue with the correct identifier
        Fileformat ffNew = null;
        try {
            ConfigOpacCatalogue coc = ConfigOpac.getInstance().getCatalogueByName(configCatalogue);
            IOpacPlugin myImportOpac = (IOpacPlugin) PluginLoader.getPluginByTitle(PluginType.Opac, coc.getOpacType());
            ffNew = myImportOpac.search("12", catalogueId, coc, prefs);
        } catch (Exception e) {
            log.error("Exception while requesting the catalogue", e);
            Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, "Exception while requesting the catalogue inside of catalogue poller plugin: " + e
                    .getMessage());
            return false;
        }

        // if structure subelements shall be kept, merge old and new fileformat,
        // otherwise just write the new one
        try {
            if (configMergeRecords) {
                // first load logical topstruct or first child
                DocStruct topstructNew = ffNew.getDigitalDocument().getLogicalDocStruct();
                DocStruct anchorNew = null;
                DocStruct physNew = ffNew.getDigitalDocument().getPhysicalDocStruct();
                if (topstructNew.getType().isAnchor()) {
                    anchorNew = topstructNew;
                    topstructNew = topstructNew.getAllChildren().get(0);
                }
                PullDiff diff = new PullDiff();
                PollDocStruct.checkDifferences(topstructNew, topstructOld, configSkipFields, diff);
                if (anchorNew != null && anchorOld != null) {
                    PollDocStruct.checkDifferences(anchorNew, anchorOld, configSkipFields, diff);
                }
                if (physNew != null && physOld != null) {
                    PollDocStruct.checkDifferences(physNew, physOld, configSkipFields, diff);
                }

                diff.setProcessId(p.getId());
                diff.setProcessTitle(p.getTitel());
                differences.add(diff);

                // then run through all new metadata and check if these should
                // replace the old ones
                // if yes remove the old ones from the old fileformat
                mergeMetadataRecords(configSkipFields, topstructOld, topstructNew);
                if (anchorNew != null && anchorOld != null) {
                    mergeMetadataRecords(configSkipFields, anchorOld, anchorNew);
                }
                if (physNew != null && physOld != null) {
                    mergeMetadataRecords(configSkipFields, physOld, physNew);
                }

                // then write the updated old file format
                // ffOld.write(p.getMetadataFilePath());
                p.writeMetadataFile(ffOld);
            } else {
                // just write the new one and don't merge any data
                // ffNew.write(p.getMetadataFilePath());
                p.writeMetadataFile(ffNew);
            }
        } catch (Exception e) {
            log.error("Exception while writing the updated METS file into the file system", e);
            Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG,
                    "Exception while writing the updated METS file into the file system inside of catalogue poller plugin: " + e.getMessage());
            return false;
        }

        // everything finished
        log.debug("Finished with catalogue request");
        Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, "Mets file updeded by catalogue poller plugin successfully");
        return true;
    }

    /**
     * Replaces the metadata of the old docstruct with the values of the new docstruct.
     * If a metadata type of the old docstruct is marked as to skip, it gets not replaced.
     * Otherwise all old data is removed and all new metadata is added.
     * 
     * @param configSkipFields
     * @param docstructOld
     * @param docstructNew
     * @throws MetadataTypeNotAllowedException
     */

    private void mergeMetadataRecords(List<String> configSkipFields, DocStruct docstructOld, DocStruct docstructNew)
            throws MetadataTypeNotAllowedException {
        if (docstructNew.getAllMetadata() != null) {
            for (Metadata md : docstructNew.getAllMetadata()) {
                if (!configSkipFields.contains(md.getType().getName())) {
                    List<? extends Metadata> remove = docstructOld.getAllMetadataByType(md.getType());
                    if (remove != null) {
                        for (Metadata mdRm : remove) {
                            docstructOld.removeMetadata(mdRm);
                        }
                    }
                }
            }
            // now add the new metadata to the old topstruct
            for (Metadata md : docstructNew.getAllMetadata()) {
                if (!configSkipFields.contains(md.getType().getName())) {
                    docstructOld.addMetadata(md);
                }
            }
        }

        // now do the same with persons
        if (docstructNew.getAllPersons() != null) {
            for (Person pd : docstructNew.getAllPersons()) {
                if (!configSkipFields.contains(pd.getType().getName())) {
                    List<? extends Person> remove = docstructOld.getAllPersonsByType(pd.getType());
                    if (remove != null) {
                        for (Person pdRm : remove) {
                            docstructOld.removePerson(pdRm);
                        }
                    }
                }
            }
            // now add the new persons to the old topstruct
            for (Person pd : docstructNew.getAllPersons()) {
                if (!configSkipFields.contains(pd.getType().getName())) {
                    docstructOld.addPerson(pd);
                }
            }
        }

        // check if the new record contains metadata groups
        if (docstructNew.getAllMetadataGroups() != null) {
            for (MetadataGroup newGroup : docstructNew.getAllMetadataGroups()) {
                // check if the group should be skipped
                if (!configSkipFields.contains(newGroup.getType().getName())) {
                    // if not, remove the old groups of the type
                    List<MetadataGroup> groupsToRemove = docstructOld.getAllMetadataGroupsByType(newGroup.getType());
                    if (groupsToRemove != null) {
                        for (MetadataGroup oldGroup : groupsToRemove) {
                            docstructOld.removeMetadataGroup(oldGroup);
                        }
                    }
                }
            }
            // add new metadata groups
            for (MetadataGroup newGroup : docstructNew.getAllMetadataGroups()) {
                if (!configSkipFields.contains(newGroup.getType().getName())) {
                    docstructOld.addMetadataGroup(newGroup);
                }
            }
        }
    }

    public String getLastRun() {
        DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(config.getLong("lastRun", 0));
        return formatter.format(calendar.getTime());
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
            ConfigInfo ci = new ConfigInfo(rule.getString("@title"), rule.getString("filter"), rule.getString("catalogue"), rule.getString("catalogueIdentifier"), String.valueOf(rule.getBoolean("mergeRecords")), String.valueOf(rule.getList("skipField")));
            list.add(ci);
            //            HashMap<String, String> map = new HashMap<>();
            //            map.put("title", rule.getString("@title"));
            //            map.put("filter", rule.getString("filter"));
            //            map.put("configCatalogue", rule.getString("catalogue"));
            //            map.put("configCatalogueId", rule.getString("catalogueIdentifier"));
            //            map.put("configMergeRecords", String.valueOf(rule.getBoolean("mergeRecords")));
            //            map.put("configSkipFields", String.valueOf(rule.getList("skipField")));
            //            list.add(map);
        }
        return list;
    }

}

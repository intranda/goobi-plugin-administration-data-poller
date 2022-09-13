package de.intranda.goobi.plugins.cataloguePoller;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IOpacPlugin;

import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import ugh.dl.Corporate;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.IncompletePersonObjectException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;

@Data
@Log4j2
public class CataloguePoll {
    private XMLConfiguration config;
    private List<PullDiff> differences;
    private boolean testRun;

    private static final DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");

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
        List<String> configSkipFields = Arrays.asList(rule.getStringArray("skipField"));
        log.debug("Rule '" + title + "' with filter '" + filter + "'");

        // now filter the list of all processes that should be affected and
        // fun through it
        String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);

        List<Integer> processIds = ProcessManager.getIdsForFilter(query);
        for (Integer id : processIds) {
            Process process = ProcessManager.getProcessById(id);
            updateMetsFileForProcess(process, configCatalogue, searchfields, configMergeRecords, configSkipFields, exportUpdatedRecords,
                    configAnalyseSubElements, testRun);
        }

        // write last updated time into the configuration file
        try {
            rule.setProperty("lastRun", System.currentTimeMillis());
            Path configurationFile =
                    Paths.get(ConfigurationHelper.getInstance().getConfigurationFolder(), "plugin_intranda_administration_catalogue_poller.xml");
            config.save(configurationFile.toString());
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
    public boolean updateMetsFileForProcess(Process p, String configCatalogue, List<StringPair> searchfields, boolean configMergeRecords,
            List<String> configSkipFields, boolean exportUpdatedRecords, boolean configAnalyseSubElements, boolean testRun) {
        log.debug("Starting catalogue request using catalogue: " + configCatalogue);

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
            Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG,
                    "Exception occurred in catalogue poller plugin while reading the metadata file: " + e.getMessage());
            return false;
        }

        // create a VariableReplacer to transform the identifier field from the
        // configuration into a real value
        VariableReplacer replacer = new VariableReplacer(dd, prefs, p, null);
        List<StringPair> valueList = new ArrayList<>(searchfields.size());
        for (StringPair entry : searchfields) {
            String value = replacer.replace(entry.getTwo());
            if (StringUtils.isNotBlank(value)) {
                valueList.add(new StringPair(entry.getOne(), value));
                log.debug("Using field {} and value {} for the catalogue request", entry.getOne(), value);
            }
        }

        // request the wished catalogue with the correct identifier
        Fileformat ffNew = null;
        IOpacPlugin myImportOpac = null;
        ConfigOpacCatalogue coc = null;

        for (ConfigOpacCatalogue configOpacCatalogue : ConfigOpac.getInstance().getAllCatalogues("")) {
            if (configOpacCatalogue.getTitle().equals(configCatalogue)) {
                myImportOpac = configOpacCatalogue.getOpacPlugin();
                coc = configOpacCatalogue;
            }
        }
        if (coc == null) {
            // TODO error message
            return false;

        }

        if (myImportOpac.getTitle().equals("intranda_opac_json")) {

            try {
                Class<? extends Object> opacClass = myImportOpac.getClass();
                Method getConfigForOpac = opacClass.getMethod("getConfigForOpac");
                Object jsonOpacConfig = getConfigForOpac.invoke(myImportOpac);

                Class<? extends Object> jsonOpacConfigClass = jsonOpacConfig.getClass();

                Method getFieldList = jsonOpacConfigClass.getMethod("getFieldList");

                Object fieldList = getFieldList.invoke(jsonOpacConfig);
                List<Object> fields = (List<Object>) fieldList;
                for (StringPair sp : valueList) {
                    for (Object searchField : fields) {
                        Class<? extends Object> searchFieldClass = searchField.getClass();

                        Method getId = searchFieldClass.getMethod("getId");

                        Method setText = searchFieldClass.getMethod("setText", String.class);
                        Method setSelectedField = searchFieldClass.getMethod("setSelectedField", String.class);

                        Object id = getId.invoke(searchField);
                        if (((String) id).equals(sp.getOne())) {
                            String value = sp.getTwo();
                            if (StringUtils.isNotBlank(value)) {
                                setText.invoke(searchField, value);
                                setSelectedField.invoke(searchField, sp.getOne());
                            }
                        }
                    }

                }
                Method search = opacClass.getMethod("search", String.class, String.class, ConfigOpacCatalogue.class, Prefs.class);

                ffNew = (Fileformat) search.invoke(myImportOpac, "", "", coc, prefs);

            } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                return false;
            }
        } else {

            try {
                coc = ConfigOpac.getInstance().getCatalogueByName(configCatalogue);
                myImportOpac = (IOpacPlugin) PluginLoader.getPluginByTitle(PluginType.Opac, coc.getOpacType());
                ffNew = myImportOpac.search(valueList.get(0).getOne(), valueList.get(0).getTwo(), coc, prefs);
            } catch (Exception e) {
                log.error("Exception while requesting the catalogue", e);
                Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG,
                        "Exception while requesting the catalogue inside of catalogue poller plugin: " + e.getMessage());
                return false;
            }
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

                if (configAnalyseSubElements) {
                    List<DocStruct> dsl = topstructOld.getAllChildren();
                    if (dsl != null) {
                        MetadataType type = prefs.getMetadataTypeByName(searchfields.get(0)
                                .getTwo()
                                .replace("$", "")
                                .replace("meta.", "")
                                .replace("topstruct.", "")
                                .replace("firstchild.", "")
                                .replace("(", "")
                                .replace("{", "")
                                .replace("}", "")
                                .replace(")", ""));
                        for (DocStruct ds : dsl) {
                            getMetadataForChild(configSkipFields, prefs, myImportOpac, coc, diff, type, ds);
                        }
                    }
                }

                if (diff.getMessages() != null && !diff.getMessages().isEmpty() && !testRun) {

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

                    String processlog = "Mets file updated by catalogue poller plugin successfully" + "<br/>";
                    processlog += "<ul>";
                    for (String s : diff.getMessages()) {
                        processlog += "<li>" + s + "</li>";
                    }
                    processlog += "</ul>";
                    Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, processlog);

                    // if the record was updated and it shall be exported again then do it now
                    if (exportUpdatedRecords) {
                        exportProcess(p);
                    }

                }

            } else {
                // just write the new one and don't merge any data
                // ffNew.write(p.getMetadataFilePath());
                if (!testRun) {
                    p.writeMetadataFile(ffNew);
                    Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, "New Mets file successfully created by catalogue poller plugin");
                }
            }
        } catch (Exception e) {
            log.error("Exception while writing the updated METS file into the file system", e);
            Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG,
                    "Exception while writing the updated METS file into the file system inside of catalogue poller plugin: " + e.getMessage());
            return false;
        }

        // everything finished
        log.debug("Finished with catalogue request");
        return true;
    }

    private void getMetadataForChild(List<String> configSkipFields, Prefs prefs, IOpacPlugin myImportOpac, ConfigOpacCatalogue coc, PullDiff diff,
            MetadataType type, DocStruct ds) throws Exception, PreferencesException {
        List<? extends Metadata> identifierList = ds.getAllMetadataByType(type);
        if (identifierList != null && !identifierList.isEmpty()) {
            String identifier = identifierList.get(0).getValue();
            Fileformat ff = myImportOpac.search("12", identifier, coc, prefs);
            PollDocStruct.checkDifferences(ff.getDigitalDocument().getLogicalDocStruct(), ds, configSkipFields, diff);
            mergeMetadataRecords(configSkipFields, ds, ff.getDigitalDocument().getLogicalDocStruct());
        }
        List<DocStruct> children = ds.getAllChildren();
        if (children != null && !children.isEmpty()) {
            for (DocStruct child : children) {
                getMetadataForChild(configSkipFields, prefs, myImportOpac, coc, diff, type, child);
            }
        }
    }

    /**
     * Do the export of the process without any images.
     */
    private void exportProcess(Process p) {
        try {
            IExportPlugin export = null;
            String pluginName = ProcessManager.getExportPluginName(p.getId());
            if (StringUtils.isNotEmpty(pluginName)) {
                try {
                    export = (IExportPlugin) PluginLoader.getPluginByTitle(PluginType.Export, pluginName);
                } catch (Exception e) {
                    log.error("Can't load export plugin, use default plugin", e);
                    export = new ExportDms();
                }
            }
            if (export == null) {
                export = new ExportDms();
            }
            export.setExportFulltext(false);
            export.setExportImages(false);
            export.startExport(p);
            log.info("Export finished inside of catalogue poller for process with ID " + p.getId());
            Helper.addMessageToProcessLog(p.getId(), LogType.DEBUG, "Process successfully exported by catalogue poller");
        } catch (NoSuchMethodError | Exception e) {
            log.error("Exception during the export of process " + p.getId(), e);
        }
    }

    /**
     * Replaces the metadata of the old docstruct with the values of the new docstruct. If a metadata type of the old docstruct is marked as to skip,
     * it gets not replaced. Otherwise all old data is removed and all new metadata is added.
     * 
     * @param configSkipFields
     * @param docstructOld
     * @param docstructNew
     */
    private void mergeMetadataRecords(List<String> configSkipFields, DocStruct docstructOld, DocStruct docstructNew) {

        // run through all old metadata fields and delete these if these are not in the ignorelist
        List<Metadata> allMetadata = new ArrayList<>();
        if (docstructOld.getAllMetadata() != null) {
            allMetadata = new ArrayList<>(docstructOld.getAllMetadata());
        }
        for (Metadata md : allMetadata) {
            if (!configSkipFields.contains(md.getType().getName())) {
                List<? extends Metadata> remove = docstructOld.getAllMetadataByType(md.getType());
                if (remove != null) {
                    for (Metadata mdRm : remove) {
                        docstructOld.removeMetadata(mdRm);
                    }
                }
            }
        }
        if (docstructNew.getAllMetadata() != null) {
            // now add the new metadata to the old topstruct
            for (Metadata md : docstructNew.getAllMetadata()) {
                if (!configSkipFields.contains(md.getType().getName())) {
                    try {
                        docstructOld.addMetadata(md);
                    } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                        // ignore metadata not allowed errors
                    }
                }
            }
        }

        // now do the same with persons
        List<Person> allPersons = new ArrayList<>();
        if (docstructOld.getAllPersons() != null) {
            allPersons = new ArrayList<>(docstructOld.getAllPersons());
        }
        for (Person pd : allPersons) {
            if (!configSkipFields.contains(pd.getType().getName())) {
                List<? extends Person> remove = docstructOld.getAllPersonsByType(pd.getType());
                if (remove != null) {
                    for (Person pdRm : remove) {
                        docstructOld.removePerson(pdRm);
                    }
                }
            }
        }
        if (docstructNew.getAllPersons() != null) {
            // now add the new persons to the old topstruct
            for (Person pd : docstructNew.getAllPersons()) {
                if (!configSkipFields.contains(pd.getType().getName())) {
                    try {
                        docstructOld.addPerson(pd);
                    } catch (MetadataTypeNotAllowedException | IncompletePersonObjectException e) {
                        // ignore metadata not allowed errors
                    }
                }
            }
        }

        // corporates
        List<Corporate> allCorporates = new ArrayList<>();
        if (docstructOld.getAllCorporates() != null) {
            allCorporates = new ArrayList<>(docstructOld.getAllCorporates());
        }
        for (Corporate corporate : allCorporates) {
            if (!configSkipFields.contains(corporate.getType().getName())) {
                List<? extends Corporate> remove = docstructOld.getAllCorporatesByType(corporate.getType());
                if (remove != null) {
                    for (Corporate pdRm : remove) {
                        docstructOld.removeCorporate(pdRm);
                    }
                }
            }
        }
        if (docstructNew.getAllCorporates() != null) {
            // now add the new persons to the old topstruct
            for (Corporate pd : docstructNew.getAllCorporates()) {
                if (!configSkipFields.contains(pd.getType().getName())) {
                    try {
                        docstructOld.addCorporate(pd);
                    } catch (MetadataTypeNotAllowedException | IncompletePersonObjectException e) {
                        // ignore metadata not allowed errors
                    }
                }
            }
        }

        // check if the new record contains metadata groups
        List<MetadataGroup> allGroups = new ArrayList<>();
        if (docstructOld.getAllMetadataGroups() != null) {
            allGroups = new ArrayList<>(docstructOld.getAllMetadataGroups());
        }
        for (MetadataGroup group : allGroups) {
            // check if the group should be skipped
            if (!configSkipFields.contains(group.getType().getName())) {
                // if not, remove the old groups of the type
                List<MetadataGroup> groupsToRemove = docstructOld.getAllMetadataGroupsByType(group.getType());
                if (groupsToRemove != null) {
                    for (MetadataGroup oldGroup : groupsToRemove) {
                        docstructOld.removeMetadataGroup(oldGroup);
                    }
                }
            }
        }
        // add new metadata groups
        if (docstructNew.getAllMetadataGroups() != null) {
            for (MetadataGroup newGroup : docstructNew.getAllMetadataGroups()) {
                if (!configSkipFields.contains(newGroup.getType().getName())) {
                    try {
                        docstructOld.addMetadataGroup(newGroup);
                    } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                        // ignore metadata not allowed errors
                    }
                }
            }
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
            ci.setSkipFields(String.valueOf(rule.getList("skipField")));

            ci.setExportUpdatedRecords(rule.getBoolean("exportUpdatedRecords"));
            ci.setAnalyseSubElements(rule.getBoolean("analyseSubElements"));
            ci.setStartTime(rule.getString("@startTime"));
            ci.setDelay(rule.getString("@delay"));

            ci.setLastRun(formatter.format(calendar.getTime()));

            list.add(ci);
        }
        return list;
    }

}

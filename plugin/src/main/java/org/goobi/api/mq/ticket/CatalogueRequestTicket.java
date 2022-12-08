package org.goobi.api.mq.ticket;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.api.mq.TaskTicket;
import org.goobi.api.mq.TicketHandler;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IExportPlugin;

import de.intranda.goobi.plugins.datapoller.CatalogueHandler;
import de.intranda.goobi.plugins.datapoller.CatalogueHandlerException;
import de.intranda.goobi.plugins.datapoller.PollDocStruct;
import de.intranda.goobi.plugins.datapoller.PullDiff;
import de.intranda.goobi.plugins.datapoller.xls.FileManager;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
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
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.MetsMods;

@Log4j2
public class CatalogueRequestTicket implements TicketHandler<PluginReturnValue> {

    @Override
    public PluginReturnValue call(TaskTicket ticket) {
        log.info("got CatalogueRequest ticket for {}", ticket.getProcessId());
        PullDiff diff = null;
        Integer processId = ticket.getProcessId();
        Process process = null;

        // get configured rules from ticket
        boolean mergeRecords = Boolean.parseBoolean(ticket.getProperties().get("mergeRecords"));
        boolean analyseSubElements = Boolean.parseBoolean(ticket.getProperties().get("analyseSubElements"));
        boolean exportUpdatedRecords = Boolean.parseBoolean(ticket.getProperties().get("exportUpdatedRecords"));
        boolean testRun = Boolean.parseBoolean(ticket.getProperties().get("testRun"));
        boolean blockList = Boolean.parseBoolean(ticket.getProperties().get("blockList"));
        // this will be set to false when the process is created by the ticket
        boolean isExistingProcess = true;
        String lastRunMillis = ticket.getProperties().get("lastRunMillis");
        String xmlTempFolder = ticket.getProperties().get("xmlTempFolder");
        String catalogueName = ticket.getProperties().get("catalogueName");
        String searchFieldsAsString = ticket.getProperties().get("searchfields");
        List<StringPair> searchfields = new ArrayList<>();
        Path hotfolderFile = null;
        // if the processId is negative we are handling a run with paths instead of processes
        if (processId < 0) {
            hotfolderFile = Paths.get(ticket.getProperties().get("hotfolderFile"));
            String processName = FilenameUtils.removeExtension(hotfolderFile.getFileName().toString());
            boolean createMissingProcesses = Boolean.parseBoolean(ticket.getProperties().get("createMissingProcesses"));
            String publicationType = ticket.getProperties().get("publicationType");
            String workflowTemplate = ticket.getProperties().get("workflow");

            if (StorageProvider.getInstance().isFileExists(hotfolderFile)) {
                //maybe add a filter for a specfic metadatvalue here later
                process = ProcessManager.getProcessByExactTitle(processName);

                //create process if it does not exist already... in any other case continue as usual
                try {
                    if (!testRun && process == null && createMissingProcesses) {
                        isExistingProcess = false;
                        Process template = ProcessManager.getProcessByExactTitle(workflowTemplate);
                        Prefs prefs = template.getRegelsatz().getPreferences();
                        Fileformat ff;

                        ff = new MetsMods(prefs);

                        DigitalDocument dd = new DigitalDocument();
                        ff.setDigitalDocument(dd);

                        // add the physical basics
                        DocStruct physical = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
                        dd.setPhysicalDocStruct(physical);

                        //TODO add publicationType
                        DocStruct logic = dd.createDocStruct(prefs.getDocStrctTypeByName(publicationType));
                        dd.setLogicalDocStruct(logic);
                        // save the process
                        BeanHelper bhelp = new BeanHelper();
                        process = bhelp.createAndSaveNewProcess(template, processName, ff);
                        log.info("{}: Process successfully created with ID: {} and name {}", getTicketHandlerName(), process.getId(),
                                process.getTitel());

                        // add some properties
                        bhelp.EigenschaftHinzufuegen(process, "Template", template.getTitel());
                        bhelp.EigenschaftHinzufuegen(process, "TemplateID", "" + template.getId());
                    }
                } catch (PreferencesException | TypeNotAllowedForParentException e) {
                    log.error("", e);
                    diff = new PullDiff(processId, processName, true, "Error trying to create Process");
                    PullDiff.marshalPullDiff(diff, xmlTempFolder, lastRunMillis);
                    return PluginReturnValue.ERROR;
                }
                if (process != null) {
                    processId = process.getId();
                }
            }

        } else {
            process = ProcessManager.getProcessById(processId);
        }
        diff = new PullDiff();
        String[] fields = searchFieldsAsString.split("\\|");

        for (String f : fields) {
            StringPair sp = new StringPair();
            String[] parts = f.split("=");
            sp.setOne(parts[0]);
            sp.setTwo(parts[1]);
            searchfields.add(sp);
        }
        String fieldFilter = ticket.getProperties().get("fieldFilter");
        List<String> fieldFilterList = Arrays.asList(fieldFilter.split("\\|"));
        if (testRun && process == null) {
            log.debug("DataPollerPlugin: Processes will not be created during a testrun: {}", hotfolderFile.toString());
            diff = new PullDiff(processId, "", false, "Processes will not be created during a testrun:");
            PullDiff.marshalPullDiff(diff, xmlTempFolder, lastRunMillis);
            return PluginReturnValue.FINISH;
        }
        if (!updateMetsFileForProcess(process, catalogueName, searchfields, mergeRecords, fieldFilterList, exportUpdatedRecords, analyseSubElements,
                testRun, blockList, diff)) {
            FileManager.moveCatalogueFile(hotfolderFile, null);
            return PluginReturnValue.ERROR;
        }

        if (!isExistingProcess) {
            // start any open automatic tasks for the created/updated process
            log.debug("Trying to start steps with status open for process with Id {}!", process.getId());
            for (Step s : process.getSchritteList()) {
                if (StepStatus.OPEN.equals(s.getBearbeitungsstatusEnum()) && s.isTypAutomatisch()) {
                    ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
                    myThread.startOrPutToQueue();
                    log.debug("Put open step {}! into queue for process with id {}", s.getTitel(), process.getId());
                }
            }
        }

        // marshall PullDiff
        PullDiff.marshalPullDiff(diff, xmlTempFolder, lastRunMillis);
        try {
            FileManager.moveCatalogueFile(hotfolderFile, process.getImportDirectory());
        } catch (SwapException | IOException ex) {
            log.debug("Couldn't save the imported xml file in the import directory of the process " + ex);
        }
        return PluginReturnValue.FINISH;
    }

    @Override
    public String getTicketHandlerName() {
        return "CatalogueRequest";
    }

    /**
     * Method to update the mets file of a process with new data
     * 
     * @param p
     * @param configCatalogue the catalogue name to use (e.g. GBV, Wiener, ...)
     * @param configCatalogueId the identifierfield to use (e.g. $(meta.CatalogIDDigital))
     * @param configMergeRecords define if the content shall be merged (true) or overwritten (false)
     * @param fieldFilterList define a list of fields that shall now be updated during merging (e.g. CatalogueIDDigital, DocLanguage ...)
     * @return
     */
    public boolean updateMetsFileForProcess(Process p, String configCatalogue, List<StringPair> searchfields, boolean configMergeRecords,
            List<String> fieldFilterList, boolean exportUpdatedRecords, boolean configAnalyseSubElements, boolean testRun, boolean isBlockList,
            PullDiff diff) {
        log.debug("Starting catalogue request using catalogue: {}", configCatalogue);
        if (diff == null) {
            log.debug("PullDiff is not allowed to be null  {}", p.getId());
            return false;
        }
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
                Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG, "Metadata file is not readable for catalogue poller plugin");
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
            Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG,
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
        try {
            CatalogueHandler catHandler = new CatalogueHandler(configCatalogue, valueList, prefs);

            if (catHandler.getFfNew() == null) {
                diff.setFailed(true);
                log.debug("DataPollerPlugin: OPAC-Search returned no Fileformat for pocess with id {}", p.getId());
                diff.setDebugMessage("OPAC-Search returned no Fileformat");
                return false;
            }
            // if structure subelements shall be kept, merge old and new fileformat,
            // otherwise just write the new one
            try {
                if (configMergeRecords) {
                    // first load logical topstruct or first child
                    DocStruct topstructNew = catHandler.getFfNew().getDigitalDocument().getLogicalDocStruct();
                    DocStruct anchorNew = null;
                    DocStruct physNew = catHandler.getFfNew().getDigitalDocument().getPhysicalDocStruct();
                    if (topstructNew.getType().isAnchor()) {
                        anchorNew = topstructNew;
                        topstructNew = topstructNew.getAllChildren().get(0);
                    }
                    diff.setMergeRecords(true);
                    PollDocStruct.checkDifferences(topstructNew, topstructOld, fieldFilterList, diff, isBlockList);
                    if (anchorNew != null && anchorOld != null) {
                        PollDocStruct.checkDifferences(anchorNew, anchorOld, fieldFilterList, diff, isBlockList);
                    }
                    if (physNew != null && physOld != null) {
                        PollDocStruct.checkDifferences(physNew, physOld, fieldFilterList, diff, isBlockList);
                    }

                    diff.setProcessId(p.getId());
                    diff.setProcessTitle(p.getTitel());

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
                                getMetadataForChild(fieldFilterList, prefs, catHandler, diff, type, ds, isBlockList);
                            }
                        }
                    }

                    if (diff.getMessages() != null && !diff.getMessages().isEmpty() && !testRun) {

                        // then run through all new metadata and check if these should
                        // replace the old ones
                        // if yes remove the old ones from the old fileformat
                        mergeMetadataRecords(fieldFilterList, topstructOld, topstructNew, isBlockList);
                        if (anchorNew != null && anchorOld != null) {
                            mergeMetadataRecords(fieldFilterList, anchorOld, anchorNew, isBlockList);
                        }
                        if (physNew != null && physOld != null) {
                            mergeMetadataRecords(fieldFilterList, physOld, physNew, isBlockList);
                        }

                        // then write the updated old file format
                        p.writeMetadataFile(ffOld);

                        StringBuilder processlog = new StringBuilder("Mets file updated by catalogue poller plugin successfully" + "<br/>");
                        processlog.append("<ul>");
                        for (String s : diff.getMessages()) {
                            processlog.append("<li>" + s + "</li>");
                        }
                        processlog.append("</ul>");
                        Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG, processlog.toString());

                        // if the record was updated and it shall be exported again then do it now
                        if (exportUpdatedRecords) {
                            exportProcess(p);
                        }
                    }

                } else {// just write the new one and don't merge any data
                    String message;
                    if (!testRun) {
                        p.writeMetadataFile(catHandler.getFfNew());
                        Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG, "New Mets file successfully created by catalogue poller plugin");
                        diff.reset(p.getId(), p.getTitel(), false, "FileFormat was replaced no Diff was created!");
                        diff.setMergeRecords(false);
                    } else {
                        diff.reset(p.getId(), p.getTitel(), false, "FileFormat was replaced no Diff was created!");
                        diff.setMergeRecords(false);
                    }

                }
            } catch (CatalogueHandlerException ex) {
                Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG, "Error opening the catalogue:" + ex.getMessage());
            }
        } catch (Exception e) {
            log.error("Exception while writing the updated METS file into the file system", e);
            Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG,
                    "Exception while writing the updated METS file into the file system inside of catalogue poller plugin: " + e.getMessage());

        }

        // everything finished
        log.debug("Finished with catalogue request");
        return true;
    }

    /**
     * Replaces the metadata of the old docstruct with the values of the new docstruct. If a metadata type of the old docstruct is marked as to skip,
     * it gets not replaced. Otherwise all old data is removed and all new metadata is added.
     * 
     * @param fieldFilterList
     * @param docstructOld
     * @param docstructNew
     */
    private void mergeMetadataRecords(List<String> fieldFilterList, DocStruct docstructOld, DocStruct docstructNew, boolean isBlockList) {

        // run through all old metadata fields and delete these if these are not in the ignorelist
        List<Metadata> allMetadata = new ArrayList<>();
        if (docstructOld.getAllMetadata() != null) {
            allMetadata = new ArrayList<>(docstructOld.getAllMetadata());
        }
        for (Metadata md : allMetadata) {
            boolean removeMetadata = fieldFilterList.contains(md.getType().getName());
            // if the list is a blackList the behaviour shall be inversed
            removeMetadata = (isBlockList) ? !removeMetadata : removeMetadata;

            if (removeMetadata) {
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
                boolean addMetadata = fieldFilterList.contains(md.getType().getName());
                // if the list is a blackList the behaviour shall be inversed
                addMetadata = (isBlockList) ? !addMetadata : addMetadata;

                if (addMetadata) {
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
            boolean removePerson = fieldFilterList.contains(pd.getType().getName());
            // if the list is a blackList the behaviour shall be inversed
            removePerson = (isBlockList) ? !removePerson : removePerson;
            if (removePerson) {
                List<? extends Person> remove = docstructOld.getAllPersonsByType(pd.getType());
                if (remove != null) {
                    for (Person pdRm : remove) {
                        docstructOld.removePerson(pdRm);
                    }
                }
            }
        }
        if (docstructNew.getAllPersons() != null) {

            for (Person pd : docstructNew.getAllPersons()) {
                // now add the new persons to the old topstruct
                boolean personExists = fieldFilterList.contains(pd.getType().getName());
                // if the list is a blackList the behaviour shall be inversed
                boolean addPerson = (isBlockList) ? !personExists : personExists;
                if (addPerson) {
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
            boolean removeCorporate = fieldFilterList.contains(corporate.getType().getName());
            // if the list is a blackList the behaviour shall be inversed
            removeCorporate = (isBlockList) ? !removeCorporate : removeCorporate;
            if (removeCorporate) {
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
            for (Corporate corporate : docstructNew.getAllCorporates()) {
                boolean addCorporate = fieldFilterList.contains(corporate.getType().getName());
                // if the list is a blackList the behaviour shall be inversed
                addCorporate = (isBlockList) ? !addCorporate : addCorporate;
                if (addCorporate) {
                    try {
                        docstructOld.addCorporate(corporate);
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
            boolean removeGroup = fieldFilterList.contains(group.getType().getName());
            // if the list is a blackList the behaviour shall be inversed
            removeGroup = (isBlockList) ? !removeGroup : removeGroup;
            if (removeGroup) {
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
                boolean addGroup = fieldFilterList.contains(newGroup.getType().getName());
                // if the list is a blackList the behaviour shall be inversed
                if (isBlockList) {
                    addGroup = !addGroup;
                }
                if (!fieldFilterList.contains(newGroup.getType().getName())) {
                    try {
                        docstructOld.addMetadataGroup(newGroup);
                    } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                        // ignore metadata not allowed errors
                    }
                }
            }
        }
    }

    public void getMetadataForChild(List<String> fieldFilterList, Prefs prefs, CatalogueHandler catHandler, PullDiff diff, MetadataType type,
            DocStruct ds, boolean isBlockList) throws Exception {
        List<? extends Metadata> identifierList = ds.getAllMetadataByType(type);
        if (identifierList != null && !identifierList.isEmpty()) {
            String identifier = identifierList.get(0).getValue();
            Fileformat ff = catHandler.getMyImportOpac().search("12", identifier, catHandler.getCoc(), prefs);
            PollDocStruct.checkDifferences(ff.getDigitalDocument().getLogicalDocStruct(), ds, fieldFilterList, diff, isBlockList);
            mergeMetadataRecords(fieldFilterList, ds, ff.getDigitalDocument().getLogicalDocStruct(), isBlockList);
        }
        List<DocStruct> children = ds.getAllChildren();
        if (children != null && !children.isEmpty()) {
            for (DocStruct child : children) {
                getMetadataForChild(fieldFilterList, prefs, catHandler, diff, type, child, isBlockList);
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
            Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG, "Process successfully exported by catalogue poller");
        } catch (NoSuchMethodError | Exception e) {
            log.error("Exception during the export of process " + p.getId(), e);
        }
    }

}

package org.goobi.api.mq.ticket;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.easymock.EasyMock;
import org.goobi.api.mq.ticket.CatalogueRequestTicket;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.opac.PicaPlus;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "jdk.internal.reflect.*", "javax.management.*" })
@PrepareForTest({ ConfigPlugins.class, ConfigOpac.class, Helper.class, PluginLoader.class, MetadataManager.class })
public class CatalogueRequestTicketTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    public static DigitalDocument document;
    public static DocStruct dsOld;
    public static DocStruct dsNew;
    public static Path ruleSet;
    private Path processDirectory;
    private static String resourcesFolder;
    private static Path metaSource;
    private static Path metaTarget;

    private static Path defaultGoobiConfig;

    private Process process;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse
        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }
        ruleSet = Paths.get(resourcesFolder, "ruleset.xml");
        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse
        System.setProperty("log4j.configurationFile", log4jFile);

        Path template = Paths.get(CatalogueRequestTicketTest.class.getClassLoader().getResource(".").getFile());
        defaultGoobiConfig = Paths.get(template.getParent().getParent().toString() + "/src/test/resources/config/goobi_config.properties"); // for junit tests in eclipse
        if (!Files.exists(defaultGoobiConfig)) {
            defaultGoobiConfig = Paths.get("target/test-classes/config/goobi_config.properties"); // to run mvn test from cli or in jenkins
        }
    }

    @Before
    public void setUp() throws Exception {

        Path goobiMainFolder = folder.newFolder("goobi").toPath();
        Path configFolder = Paths.get(goobiMainFolder.toString(), "config");
        Files.createDirectories(configFolder);

        // configure config folder
        Path configFile = Paths.get(configFolder.toString(), "goobi_config.properties");
        Path opacFile = Paths.get(configFolder.toString(), "goobi_opac.xml");
        Files.copy(defaultGoobiConfig, configFile);
        Files.copy(Paths.get(defaultGoobiConfig.getParent().toString(), "goobi_opac.xml"), opacFile);

        ConfigurationHelper.CONFIG_FILE_NAME = configFile.toString();

        ConfigurationHelper.resetConfigurationFile();
        ConfigurationHelper.getInstance().setParameter("goobiFolder", goobiMainFolder.toString() + "/");

        // create  folders
        processDirectory = Paths.get(goobiMainFolder.toString(), "metadata", "1");
        Files.createDirectories(processDirectory);
        Path rulesetDirectory = Paths.get(goobiMainFolder.toString(), "rulesets");
        Files.createDirectories(rulesetDirectory);
        Files.copy(Paths.get(defaultGoobiConfig.getParent().getParent().toString(), "ruleset.xml"),
                Paths.get(rulesetDirectory.toString(), "ruleset.xml"));

        // copy meta.xml
        metaSource = Paths.get(resourcesFolder, "meta.xml");
        metaTarget = processDirectory.resolve("meta.xml");
        Files.copy(metaSource, metaTarget);

        process = getProcess();

        PowerMock.mockStatic(Helper.class);

        EasyMock.expect(Helper.getTranslation(EasyMock.anyString())).andReturn("").anyTimes();
        Helper.addMessageToProcessLog(EasyMock.anyInt(), EasyMock.anyObject(LogType.class), EasyMock.anyString());

        PowerMock.mockStatic(ConfigOpac.class);
        ConfigOpac co = EasyMock.createMock(ConfigOpac.class);
        IOpacPlugin plugin = EasyMock.createMock(IOpacPlugin.class);
        ConfigOpacCatalogue coc = EasyMock.createMock(ConfigOpacCatalogue.class);

        Fileformat opacResponse = null;
        Prefs prefs = process.getRegelsatz().getPreferences();
        opacResponse = new PicaPlus(prefs);
        opacResponse.read(Paths.get(resourcesFolder, "opac_618299084.xml").toString());

        List<ConfigOpacCatalogue> cocList = new ArrayList<>();
        cocList.add(coc);
        EasyMock.expect(ConfigOpac.getInstance()).andReturn(co).anyTimes();
        EasyMock.expect(co.getAllCatalogues(EasyMock.anyString())).andReturn(cocList).anyTimes();
        EasyMock.expect(co.getCatalogueByName(EasyMock.anyString())).andReturn(coc).anyTimes();
        EasyMock.expect(coc.getOpacPlugin()).andReturn(plugin).anyTimes();
        EasyMock.expect(coc.getOpacType()).andReturn("Pica").anyTimes();

        EasyMock.expect(coc.getTitle()).andReturn("K10Plus").anyTimes();
        EasyMock.expect(plugin.getTitle()).andReturn("Pica").anyTimes();

        EasyMock.expect(plugin.search(EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyObject(), EasyMock.anyObject()))
                .andReturn(opacResponse)
                .anyTimes();

        PowerMock.mockStatic(PluginLoader.class);
        EasyMock.expect(PluginLoader.getPluginByTitle(PluginType.Opac, "Pica")).andReturn(plugin).anyTimes();
        PowerMock.mockStatic(MetadataManager.class);
        //ProcessManager.saveProcess(EasyMock.anyObject(Process.class));
        MetadataManager.updateMetadata(EasyMock.anyInt(), EasyMock.anyObject(Map.class));
        MetadataManager.updateJSONMetadata(EasyMock.anyInt(), EasyMock.anyObject(Map.class));

        EasyMock.replay(co);
        EasyMock.replay(coc);
        EasyMock.replay(plugin);

        PowerMock.replay(PluginLoader.class);
        PowerMock.replay(Helper.class);
        PowerMock.replay(ConfigOpac.class);
        PowerMock.replay(MetadataManager.class);
    }

    @Test
    public void updateMetsFileForProcessTest() throws IOException {
        CatalogueRequestTicket catPollTicket = new CatalogueRequestTicket();
        List<StringPair> catalogueList = new ArrayList<>();
        StringPair sp = new StringPair("12", "618299084");
        catalogueList.add(sp);
        List<String> filter = new ArrayList<>();
        filter.add("PublicationYear");
        long beforeCall = StorageProvider.getInstance().getLastModifiedDate(metaTarget);
        // run updateMetsFileForProcess in test mode
        catPollTicket.updateMetsFileForProcess(process, "K10Plus", catalogueList, true, filter, false, true, true, false);
        long afterCall = StorageProvider.getInstance().getLastModifiedDate(metaTarget);
        assertEquals("The meta.xml-file was changed!", beforeCall, afterCall);
    }

    public Process getProcess() {
        Project project = new Project();
        project.setTitel("Archive_Project");
        project.setId(11);
        Process process = new Process();
        process.setTitel("dissmeind_618299084");
        process.setProjekt(project);
        process.setId(1);

        Ruleset ruleset = new Ruleset();
        ruleset.setId(11111);
        ruleset.setOrderMetadataByRuleset(true);
        ruleset.setTitel("ruleset.xml");
        ruleset.setDatei("ruleset.xml");
        process.setRegelsatz(ruleset);
        try {
            createProcessDirectory();
        } catch (IOException e) {
        }
        return process;
    }

    private void createProcessDirectory() throws IOException {
        // image folder
        Path imageDirectory = processDirectory.resolve("images");
        Files.createDirectory(imageDirectory);
        // master folder
        Files.createDirectory(imageDirectory.resolve("dissmeind_618299084_master"));
        // media folder
        Files.createDirectory(imageDirectory.resolve("dissmeind_618299084_media"));
    }
}

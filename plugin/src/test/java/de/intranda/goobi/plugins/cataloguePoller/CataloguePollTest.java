package de.intranda.goobi.plugins.cataloguePoller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.shiro.util.Assert;
import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.production.cli.helper.StringPair;
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
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "jdk.internal.reflect.*", "javax.management.*" })
@PrepareForTest({ ConfigPlugins.class, ConfigurationHelper.class, MetadatenHelper.class, ProcessManager.class, MetadataManager.class })
public class CataloguePollTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    public static DigitalDocument document;
    public static DocStruct dsOld;
    public static DocStruct dsNew;
    public static Path ruleSet;
    private Path processDirectory;
    private Path metadataDirectory;
    private Process process;
    private Prefs prefs;
    private static String resourcesFolder;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse
        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }
        ruleSet = Paths.get(resourcesFolder + "ruleset.xml");
        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse
        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Before
    public void setUp() throws Exception {

        // create  folders
        metadataDirectory = folder.newFolder("metadata").toPath();
        processDirectory = metadataDirectory.resolve("1");
        Files.createDirectories(processDirectory);
        // copy meta.xml
        Path metaSource = Paths.get(resourcesFolder + "meta.xml");
        Path metaTarget = processDirectory.resolve("meta.xml");
        Files.copy(metaSource, metaTarget);

        // return empty configuration
        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(new XMLConfiguration()).anyTimes();
        PowerMock.replay(ConfigPlugins.class);

        prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        Fileformat ff = new MetsMods(prefs);
        ff.read(metaTarget.toString());

        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();
        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectory.toString() + File.separator).anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("dissmeind_618299084_media").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMasterDirectoryName()).andReturn("dissmeind_618299084_master").anyTimes();
        EasyMock.expect(configurationHelper.getProcessOcrTxtDirectoryName()).andReturn("dissmeind_618299084_ocrtxt").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesSourceDirectoryName()).andReturn("dissmeind_618299084_source").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImportDirectoryName()).andReturn("dissmeind_618299084_import").anyTimes();
        EasyMock.expect(configurationHelper.getGoobiFolder()).andReturn("").anyTimes();
        EasyMock.expect(configurationHelper.getScriptsFolder()).andReturn("").anyTimes();
        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn("").anyTimes();
        EasyMock.expect(configurationHelper.isCreateSourceFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseMasterDirectory()).andReturn(true).anyTimes();
        EasyMock.expect(configurationHelper.isCreateMasterDirectory()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getNumberOfMetaBackups()).andReturn(0).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiUrl()).andReturn("http://127.0.0.1:80/").anyTimes();
        EasyMock.replay(configurationHelper);
        PowerMock.replay(ConfigurationHelper.class);

        PowerMock.mockStatic(MetadatenHelper.class);
        EasyMock.expect(MetadatenHelper.getMetaFileType(EasyMock.anyString())).andReturn("mets").anyTimes();
        EasyMock.expect(MetadatenHelper.getFileformatByName(EasyMock.anyString(), EasyMock.anyObject())).andReturn(ff).anyTimes();
        EasyMock.expect(MetadatenHelper.getMetadataOfFileformat(EasyMock.anyObject(), EasyMock.anyBoolean()))
                .andReturn(Collections.emptyMap())
                .anyTimes();
        PowerMock.replay(MetadatenHelper.class);

    }

    @Test
    public void updateMetsFileForProcessTest() {
        CataloguePoll catPoll = new CataloguePoll();
        Process p = getProcess();
        List<StringPair> catalogueList = new ArrayList<>();
        StringPair sp = new StringPair("12", "618299084");
        catalogueList.add(sp);
        List<String> filter = new ArrayList<>();
        filter.add("PublicationYear");
        filter.add("Author");
        catPoll.updateMetsFileForProcess(p, "K10Plus", catalogueList, true, filter, false, true, true, false);
        Assert.isTrue(true);
        throw new IllegalArgumentException("look in the tempfolder");

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

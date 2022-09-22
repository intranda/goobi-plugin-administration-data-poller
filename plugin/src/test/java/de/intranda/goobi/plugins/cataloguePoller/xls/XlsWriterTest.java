package de.intranda.goobi.plugins.cataloguePoller.xls;

import static org.junit.Assert.fail;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.file.PathUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;


import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;
import de.intranda.goobi.plugins.cataloguePoller.xls.XlsData;
import de.intranda.goobi.plugins.cataloguePoller.xls.XlsWriter;


public class XlsWriterTest {
    private XlsWriter xlsWriter;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        try {
            Path destination = folder.newFolder("output").toPath(); 
            Files.createDirectories(destination);
            this.xlsWriter = new XlsWriter(destination);
        } catch (IOException e) {
            fail("Cannot prepare output folder");
        }
    }
    @Test
    public void testWriteWorkbook() {
        //Prepare Test
        long date =1663242175977L;
        String ruleName = "Archive Project";
        String fileName = "archive_project-2022-09-15-13-42-55.xlsx";
        List<PullDiff> diff = new ArrayList<PullDiff>();
        PullDiff pd = new PullDiff();
        pd.setProcessId(123);
        pd.setProcessTitle("TestTitle");
        XlsData xlsData = new XlsData("TitleDocMain","oldValue","newValue");
        List<XlsData> xlsDataList = new ArrayList<XlsData>();
        pd.setXlsData(xlsDataList);
        diff.add(pd);

        Path result = xlsWriter.writeWorkbook(diff ,date, ruleName, true);
        
        //Tests
        Assert.assertEquals("Wrong composition of file name!",fileName,result.getFileName().toString());
        Assert.assertTrue("No File was created!",Files.exists(result));
    }
    
//    @After
//    public void cleanup() {
//        try {
//            if (Files.exists(outputFolder)) {
//                PathUtils.delete(outputFolder);
//            }
//        } catch (IOException e) {
//            fail("Cannot delete output folder " + outputFolder);
//        }
//    }

}

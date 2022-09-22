package de.intranda.goobi.plugins.cataloguePoller.xls;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;

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
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        long date = 1663242175977L;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        String ruleName = "Archive Project";
        String fileName = "archive_project-" + dateFormatter.format(calendar.getTime()) + ".xlsx";

        List<PullDiff> diff = new ArrayList<>();
        PullDiff pd = new PullDiff();
        pd.setProcessId(123);
        pd.setProcessTitle("TestTitle");
        XlsData xlsData = new XlsData("TitleDocMain", "oldValue", "newValue");
        List<XlsData> xlsDataList = new ArrayList<>();
        pd.setXlsData(xlsDataList);
        diff.add(pd);

        Path result = xlsWriter.writeWorkbook(diff, date, ruleName, true);

        //Tests
        Assert.assertEquals("Wrong composition of file name!", fileName, result.getFileName().toString());
        Assert.assertTrue("No File was created!", Files.exists(result));
    }

}

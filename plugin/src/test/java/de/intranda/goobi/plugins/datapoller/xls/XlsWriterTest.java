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
package de.intranda.goobi.plugins.datapoller.xls;

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

import de.intranda.goobi.plugins.datapoller.PullDiff;
import de.intranda.goobi.plugins.datapoller.xls.XlsData;
import de.intranda.goobi.plugins.datapoller.xls.XlsWriter;

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

        Path result = xlsWriter.writeWorkbook(diff, date, ruleName, true, false);

        //Tests
        Assert.assertEquals("Wrong composition of file name!", fileName, result.getFileName().toString());
        Assert.assertTrue("No File was created!", Files.exists(result));
    }

}

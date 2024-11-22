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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.intranda.goobi.plugins.datapoller.PullDiff;
import de.sub.goobi.helper.StorageProvider;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class XlsWriter {

    private Path path;
    private final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    public XlsWriter(Path targetFolder) {
        this.path = targetFolder;
    }

    public FolderInfo writeWorkbook(Path XmlFolder) {
        Path reportInfoPath = FileManager.getReportInfoFile(XmlFolder);
        if (reportInfoPath == null) {
            return null;
        }
        ReportInfo info = ReportInfo.unmarshalReportInfo(reportInfoPath);
        List<Path> differencesXML = FileManager.getXmlFiles(XmlFolder);
        List<PullDiff> differences = new ArrayList<>();
        if (info != null) {
            for (Path xmlFile : differencesXML) {
                differences.add(PullDiff.unmarshalPullDiff(xmlFile));
            }
            boolean unfinished = differences.size() < info.getTicketCount();
            Path xlsFile = writeWorkbook(differences, info.getLastRunMillis(), info.getRuleName(), info.isTestRun(), unfinished);
            if (xlsFile != null && differences.size() == info.getTicketCount()) {
                if (!StorageProvider.getInstance().deleteDir(XmlFolder)) {
                    log.debug("DataPollerPlugin: Couldn't delete the folder: " + XmlFolder);
                }
            }
            return new FolderInfo(xlsFile, differences, info);
        } else {
            log.error("DataPollerPlugin: Couldn't find reportInfo.xml file in {}! No xlsx-Report was created!", XmlFolder.toString());
            return null;
        }
    }

    /**
     * Creates an xlsx File with the Differences between the old docstruct and the new docstruct
     *
     * @param differences List of PullDiff Objects
     * @param lastRunMillis time of the last run in Milliseconds
     * @param ruleName name of the rule
     * @param was this run a testRun run or not
     * @return Path that points to the generated xlsx-File
     */
    public Path writeWorkbook(List<PullDiff> differences, long lastRunMillis, String ruleName, boolean testRun, boolean unfinished) {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Report Catalogue Poller");

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(lastRunMillis);

        int rowCounter = 0;
        //create header

        Row header = sheet.createRow(rowCounter++);
        writeCellsToRow(header, ruleName, (testRun) ? "test run" : "report", (unfinished) ? "interim result" : "");
        Cell cell = header.createCell(header.getLastCellNum());
        CellStyle cellStyle = wb.createCellStyle();
        cellStyle.setDataFormat((short) 14);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(calendar.getTime());

        writeCellsToRow(sheet.createRow(rowCounter++), "id", "title", "field", "old value", "new value");

        //write content
        for (PullDiff difference : differences) {
            for (XlsData data : difference.getXlsData()) {
                writeCellsToRow(sheet.createRow(rowCounter++), String.valueOf(difference.getProcessId()), difference.getProcessTitle(),
                        data.getField(), data.getOldValues(), data.getNewValues());
            }
        }

        StringBuilder fileName = new StringBuilder().append(ruleName.toLowerCase().trim().replace(" ", "_"));
        fileName.append("-").append(dateFormatter.format(calendar.getTime())).append(".xlsx");
        Path targetPath = this.path.resolve(fileName.toString());

        //write file to file system
        try (OutputStream outputFile = new FileOutputStream(targetPath.toString())) {
            wb.write(outputFile);
        } catch (IOException e) {
            log.error("DataPollerPlugin: Error writing File to Disk! No xlsx-report was created!", e);
            return null;
        } finally {
            try {
                wb.close();
            } catch (IOException e) {
                log.error("DataPollerPlugin: Error closing XSSF workbook!", e);
            }
        }
        return targetPath;
    }

    private void writeCellsToRow(Row row, String... cellContent) {
        for (int i = 0; i < cellContent.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(cellContent[i]);
        }
    }

}

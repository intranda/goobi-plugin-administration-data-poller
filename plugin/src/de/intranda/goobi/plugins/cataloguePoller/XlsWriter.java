package de.intranda.goobi.plugins.cataloguePoller;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;

public class XlsWriter {

    private Path path;
    private static final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    public XlsWriter(Path targetFolder) {
        this.path = targetFolder;
    }

    /**
     * Creates an xlsx File with the Differences between the old docstruct and the new docstruct
     * @param differences List of PullDiff Objects
     * @param lastRunMillis time of the last run in Milliseconds
     * @param ruleName name of the rule
     * @param was this run a testRun run or not
     * @return Path that points to the generated xlsx-File
     */
    public Path writeWorkbook(List<PullDiff> differences, long lastRunMillis, String ruleName, boolean testRun) {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Report Catalogue Poller");
        String timeStamp = "";
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(lastRunMillis);
        
        int RowCounter = 0;
        //create header 
      
        Row header = sheet.createRow(RowCounter++);
        writeCellsToRow(header,ruleName,(testRun) ? "Test run": "Report" );
        Cell cell = header.createCell(header.getLastCellNum());
        CellStyle cellStyle = wb.createCellStyle();
        cellStyle.setDataFormat((short)14);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(calendar.getTime());
        
        writeCellsToRow(sheet.createRow(RowCounter++), "Id", "title", "field", "old value", "new Value");
        
        //write content
        for (PullDiff difference : differences) {
            for (XlsData data : difference.getXlsData()) {
                writeCellsToRow(sheet.createRow(RowCounter++), difference.getProcessId().toString(), difference.getProcessTitle(), data.getField(),
                        data.getOldValues(), data.getNewValues());
            }
        }
        
        String fileName = ruleName.toLowerCase().trim().replace(" ", "_");
        fileName += "-"+dateFormatter.format(calendar.getTime())+".xlsx";
        Path targetPath = this.path.resolve(fileName);
        
        //write file to file system
        try (OutputStream outputFile = new FileOutputStream(targetPath.toString())) {
            wb.write(outputFile);
            wb.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return path;
    }

    private void writeCellsToRow(Row row, String... cellContent) {
        for (int i = 0; i < cellContent.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(cellContent[i]);
        }
    }

}

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
package de.intranda.goobi.plugins.cataloguePoller.xls;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import de.intranda.goobi.plugins.cataloguePoller.ConfigInfo;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.extern.log4j.Log4j2;

/**
 * Manages the xlsx-reports in the specified folder
 */
@Log4j2
public class FileManager {

    private static StorageProviderInterface SPI = StorageProvider.getInstance();

    /**
     * returns a HashMap with the latest xlsx-reports and deletes old reports
     * 
     * @param tempFolder the folder where the xlsxFiles are located
     * @param configInfos List with ConfigInfo
     * @return latest xlsx-reports in tempfolder
     */
    public static HashMap<String, Path> manageTempFiles(Path tempFolder, List<ConfigInfo> configInfos) {
        HashMap<String, Path> xlsReports = new HashMap<>();
        for (ConfigInfo configInfo : configInfos) {
            String ruleName = configInfo.getTitle();
            List<Path> xmlFolders = getXmlFolders(tempFolder, ruleName);
            Collections.sort(xmlFolders);
            if (xmlFolders != null && !xmlFolders.isEmpty()) {
                // create xlsx report
                XlsWriter writer = new XlsWriter(tempFolder);
                // put the last element (youngest file) into the HashMap
                xlsReports.put(ruleName, writer.writeWorkbook(xmlFolders.remove(xmlFolders.size() - 1)));
                // delete the rest
                for (Path xmlFolder : xmlFolders) {
                    if (!SPI.deleteDir(xmlFolder)) {
                        log.debug("CatloguePollerPlugin: Couldn't delete the folder: " + xmlFolder);
                    }
                }
            } else {
                //look for last xlsx report
                List<Path> xlsFiles = getXlsFiles(tempFolder, ruleName);
                Collections.sort(xlsFiles);
                if (xlsFiles != null && !xlsFiles.isEmpty()) {
                    // put the last element (youngest file) into the HashMap
                    xlsReports.put(ruleName, xlsFiles.remove(xlsFiles.size() - 1));
                    // delete the rest
                    for (Path xlsFile : xlsFiles) {
                        try {
                            SPI.deleteFile(xlsFile);
                        } catch (IOException e) {
                            log.error("CatloguePollerPlugin: Couldn't delete the file: " + xlsFile, e);
                        }
                    }
                }
            }
        }
        return xlsReports;
    }

    public static Path getReportInfoFile(Path xmlFolder) {
        List<Path> reportInfo = SPI.listFiles(xmlFolder.toString(), path -> {
            return !Files.isDirectory(path) && path.getFileName().toString().matches("^reportInfo.xml$");
        });
        if (!reportInfo.isEmpty()) {
            return reportInfo.get(0);
        } else {
            return null;
        }
    }

    public static Path createXmlFolder(String tempFolder, String folderName) {
        Path xmlFolder = Paths.get(tempFolder, folderName);
        try {
            SPI.createDirectories(xmlFolder);
        } catch (IOException ex) {
            log.error("CatloguePollerPlugin: Couldn't create the folder: " + xmlFolder.toString(), ex);
        }
        return xmlFolder;
    }

    private static List<Path> getXmlFolders(Path tempFolder, String ruleName) {
        return SPI.listFiles(tempFolder.toString(), path -> {
            return Files.isDirectory(path)
                    && path.getFileName().toString().matches("^catPoll_" + ruleName.toLowerCase().trim().replace(" ", "_") + "[_\\d]*$");
        });
    }

    public static List<Path> getXmlFiles(Path xmlFolder) {
        return SPI.listFiles(xmlFolder.toString(), path -> {
            return !Files.isDirectory(path) && path.getFileName().toString().matches("^[_\\d]*\\.xml$");
        });
    }

    public static List<Path> getXlsFiles(Path folder, String ruleName) {
        return SPI.listFiles(folder.toString(), path -> {
            return !Files.isDirectory(path)
                    && path.getFileName().toString().matches("^" + ruleName.toLowerCase().trim().replace(" ", "_") + "[-\\d]*\\.xlsx$");
        });
    }
}

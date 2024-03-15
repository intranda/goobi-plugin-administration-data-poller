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

import de.intranda.goobi.plugins.datapoller.ConfigInfo;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.extern.log4j.Log4j2;
import org.goobi.io.BackupFileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
    public static HashMap<String, FolderInfo> manageTempFiles(Path tempFolder, Collection<ConfigInfo> configInfos) {
        HashMap<String, FolderInfo> reports = new HashMap<>();
        for (ConfigInfo configInfo : configInfos) {
            String ruleName = configInfo.getTitle();
            List<Path> xmlFolders = getXmlFolders(tempFolder, ruleName);
            Collections.sort(xmlFolders);
            if (xmlFolders != null && !xmlFolders.isEmpty()) {
                // create xlsx report
                XlsWriter writer = new XlsWriter(tempFolder);
                // put the last element (youngest file) into the HashMap
                FolderInfo info = writer.writeWorkbook(xmlFolders.remove(xmlFolders.size() - 1));
                if (info != null) {
                    reports.put(ruleName, info);
                }
                // delete the rest
                for (Path xmlFolder : xmlFolders) {
                    if (!SPI.deleteDir(xmlFolder)) {
                        log.debug("DataPollerPlugin: Couldn't delete the folder: " + xmlFolder);
                    }
                }
            } else {
                //look for last xlsx report
                List<Path> xlsFiles = getXlsFiles(tempFolder, ruleName);
                Collections.sort(xlsFiles);
                if (xlsFiles != null && !xlsFiles.isEmpty()) {
                    // put the last element (youngest file) into the HashMap
                    reports.put(ruleName, new FolderInfo(xlsFiles.remove(xlsFiles.size() - 1)));
                    // delete the rest
                    for (Path xlsFile : xlsFiles) {
                        try {
                            SPI.deleteFile(xlsFile);
                        } catch (IOException e) {
                            log.error("DataPollerPlugin: Couldn't delete the file: " + xlsFile, e);
                        }
                    }
                }
            }
        }
        return reports;
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
            log.error("DataPollerPlugin: Couldn't create the folder: " + xmlFolder.toString(), ex);
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
            return !Files.isDirectory(path) && path.getFileName().toString().matches("^[-_\\d]*\\.xml$");
        });
    }

    public static List<Path> getXlsFiles(Path folder, String ruleName) {
        return SPI.listFiles(folder.toString(), path -> {
            return !Files.isDirectory(path)
                    && path.getFileName().toString().matches("^" + ruleName.toLowerCase().trim().replace(" ", "_") + "[-\\d]*\\.xlsx$");
        });
    }

    public static List<Path> getHotfolderFiles(String hotfolder, String filter) {
        return SPI.listFiles(hotfolder, path -> {
            return !Files.isDirectory(path) && path.getFileName().toString().matches(filter);
        });
    }

    /**
     * moves the catlogue file to its new destination after processing. null indicates failure and if a new destination is provided it's assumed that
     * the operation was successful. if the operation wasn't successful the files will be saved in an error folder in the hotfolderFilePath
     * 
     * @param hotfolderFilePath Path of the hotfolder
     * @param backupDestination destination Path where the file should be stored if the operation was successful
     */
    public static void moveCatalogueFile(Path hotfolderFilePath, String backupDestination) {
        if (hotfolderFilePath == null) {
            return;
        }
        Path fileName = hotfolderFilePath.getFileName();
        Path targetFolder =
                backupDestination != null ? hotfolderFilePath.getParent().resolve("success") : hotfolderFilePath.getParent().resolve("error");
        try {
            // check if error/ success Folders exists
            createFolderIfNotExists(targetFolder.toString());
            log.debug("Following backup destination was provided:" + backupDestination);
            if (backupDestination != null) {
                createFolderIfNotExists(backupDestination);
                BackupFileManager.createBackup(hotfolderFilePath.getParent().toString() + "/", backupDestination, fileName.toString(), 10, false);
            }
            SPI.move(hotfolderFilePath, targetFolder.resolve(fileName));
        } catch (IOException e) {
            log.error("DataPollerPlugin: Couldn't move the file: " + hotfolderFilePath.toString() + " to new location " + targetFolder.toString(), e);

        }
    }

    private static void createFolderIfNotExists(String path) throws IOException {
        Path target = Paths.get(path);
        if (!SPI.isFileExists(target)) {
            SPI.createDirectories(target);
        }
    }
}

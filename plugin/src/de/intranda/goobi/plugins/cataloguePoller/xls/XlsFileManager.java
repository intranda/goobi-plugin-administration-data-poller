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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import de.intranda.goobi.plugins.CataloguePollerPlugin;
import de.intranda.goobi.plugins.cataloguePoller.ConfigInfo;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.extern.log4j.Log4j;

/**
 * Manages the xlsx-reports in the specified folder 
 * @author mikel
 *
 */
@Log4j
public class XlsFileManager {

    private static StorageProviderInterface SPI = StorageProvider.getInstance();

    private static boolean regexFileFilter(Path path, String regex) {

        try {
            return !Files.isDirectory(path) && !Files.isHidden(path) && path.getFileName().toString().matches(regex);

        } catch (IOException e) {
            log.error("CatloguePollerPlugin: Couldn't open the file: " + path ,e);
            return false;
        }
    }

    private static List<Path> getXlsFiles(Path folder, String ruleName) {
        return SPI.listFiles(folder.toString(), path -> {
            return regexFileFilter(path, "^" + ruleName.toLowerCase().trim().replace(" ", "_") + "[-\\d]*\\.xlsx$");
        });
    }

    /**
     * returns a HashMap with the latest xlsx-reports and deletes old reports
     * @param tempFolder the folder where the xlsxFiles are located
     * @param configInfos List with ConfigInfo 
     * @return latest xlsx-reports in tempfolder
     */
    public static HashMap<String,Path> manageTempFiles(String tempFolder, List<ConfigInfo> configInfos) {
        HashMap<String, Path> xlsReports = new HashMap<String, Path>(); 
        for (ConfigInfo configInfo : configInfos) {
            String ruleName = configInfo.getTitle();
            List<Path> xlsFiles = getXlsFiles(Paths.get(tempFolder), ruleName);
            Collections.sort(xlsFiles);
            if (xlsFiles != null && xlsFiles.size() > 0) {
                xlsReports.put(ruleName,xlsFiles.get(xlsFiles.size()-1));
                if (xlsFiles.size() > 1) {
                    for (int i=0;i<xlsFiles.size()-1;i++) {
                        try {
                            SPI.deleteFile(xlsFiles.get(i));
                        } catch (IOException e) {
                            log.error("CatloguePollerPlugin: Couldn't delete the file: " + xlsFiles.get(i) ,e);
                        }
                    }
                }
            }       
        }
        return xlsReports;
    }
}

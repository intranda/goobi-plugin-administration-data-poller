package de.intranda.goobi.plugins.cataloguePoller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;

public class XlsFileManager {

    private static StorageProviderInterface SPI = StorageProvider.getInstance();

    private static boolean regexFileFilter(Path path, String regex) {

        try {
            return !Files.isDirectory(path) && !Files.isHidden(path) && path.getFileName().toString().matches(regex);

        } catch (IOException e) {
            // if we can't open it we will not add it to the List
            return false;
        }
    }

    public static List<Path> getXlsFiles(Path folder, String ruleName) {
        return SPI.listFiles(folder.toString(), path -> {
            return regexFileFilter(path, "^" + ruleName.toLowerCase().trim().replace(" ", "_") + "[-\\d]*\\.xlsx$");
        });
    }

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
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
            
        }
        return xlsReports;
    }
}

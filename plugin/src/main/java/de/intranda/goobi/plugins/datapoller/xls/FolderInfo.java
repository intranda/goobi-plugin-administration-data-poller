package de.intranda.goobi.plugins.datapoller.xls;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.intranda.goobi.plugins.datapoller.PullDiff;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FolderInfo {
    Path xlsFile;
    List<PullDiff> differences;
    ReportInfo info;

    public FolderInfo(Path xlsFile) {
        this.xlsFile = xlsFile;
        this.differences = new ArrayList<>();
        this.info = null;
    }
}

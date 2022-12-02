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

    public int getDiffSize() {
        return this.differences.size();
    }

    public List<PullDiff> getDifferences(int startIndex) {
        if (startIndex >= differences.size()) {
            return new ArrayList<>();
        }
        int offset = 500;
        int endIndex = (startIndex + offset < differences.size()) ? startIndex + offset : differences.size();
        return differences.subList(startIndex, endIndex);
    }
}

package de.intranda.goobi.plugins.datapoller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import de.intranda.goobi.plugins.datapoller.xls.XlsData;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * embedded class for the differences
 */
@Data
@Log4j2
@XmlRootElement(name = "pullDiff")
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class PullDiff {
    private int processId;
    private String processTitle;
    private boolean failed;
    private boolean mergeRecords;
    private String debugMessage;
    @XmlElementWrapper(name = "messages")
    @XmlElement(name = "message")
    private List<String> messages = new ArrayList<>();
    @XmlElementWrapper(name = "xlsDataEntries")
    @XmlElement(name = "xlsData")
    private List<XlsData> xlsData = new ArrayList<>();

    public PullDiff(Integer processId, String processTitle, boolean failed, String debugMessage) {
        this.processId = processId;
        this.processTitle = processTitle;
        this.failed = failed;
        this.debugMessage = debugMessage;

    }

    public static void marshalPullDiff(PullDiff diff, String xmlTempFolder, String lastRunMillis) {
        Path outputPath = Paths.get(xmlTempFolder);
        StorageProviderInterface spi = StorageProvider.getInstance();
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(PullDiff.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            // StringWriter sw = new StringWriter();
            // jaxbMarshaller.marshal(diff, sw);

            //Check if Folder exists and if not try to create it

            if (!spi.isFileExists(outputPath)) {
                spi.createDirectories(outputPath);
            }
            StringBuilder fileName = new StringBuilder();
            fileName.append(diff.getProcessId()).append("_").append(lastRunMillis).append(".xml");

            Path fileOutputPath = outputPath.resolve(fileName.toString());
            jaxbMarshaller.marshal(diff, new File(fileOutputPath.toString()));
        } catch (JAXBException ex) {
            log.error("DataPollerPlugin: Couldn't marshal Object to xml!", ex);
        } catch (IOException ex) {
            log.error("DataPollerPlugin: Couldn't write xml-File into folder: " + outputPath.toString());
        }
    }

    public static PullDiff unmarshalPullDiff(Path PullDiffXml) {
        PullDiff diff;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(PullDiff.class);
            Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();
            diff = (PullDiff) jaxbUnMarshaller.unmarshal(PullDiffXml.toFile());
        } catch (JAXBException ex) {
            log.error("DataPollerPlugin: Couldn't unmarshal Object from xml: " + PullDiffXml.toString(), ex);
            return null;
        }
        return diff;
    }

}

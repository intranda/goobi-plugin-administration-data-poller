package de.intranda.goobi.plugins.datapoller.xls;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;

import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement(name = "reportInfo")
public class ReportInfo {
    private boolean testRun;
    private String ruleName;
    private long lastRunMillis;
    private int ticketCount;

    public static void marshalReportInfo(ReportInfo info, Path xmlTempFolder) {
        StorageProviderInterface spi = StorageProvider.getInstance();
        Path fileOutputPath = xmlTempFolder.resolve("reportInfo.xml");
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ReportInfo.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            //Check if Folder exists and if not try to create it
            if (!spi.isFileExists(xmlTempFolder)) {
                spi.createDirectories(xmlTempFolder);
            }

            jaxbMarshaller.marshal(info, new File(fileOutputPath.toString()));
        } catch (JAXBException ex) {
            log.error("CatloguePollerPlugin: Couldn't marshal ReportInfo Object to xml!", ex);
        } catch (IOException ex) {
            log.error("CatloguePollerPlugin: Couldn't write xml-File into folder: " + fileOutputPath.toString());
        }
    }

    public static ReportInfo unmarshalReportInfo(Path ReportInfoXml) {
        ReportInfo info;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ReportInfo.class);
            Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();
            info = (ReportInfo) jaxbUnMarshaller.unmarshal(ReportInfoXml.toFile());
        } catch (JAXBException ex) {
            log.error("CatloguePollerPlugin: Couldn't unmarshal ReportInfo Object from xml: " + ReportInfoXml.toString(), ex);
            return null;
        }
        return info;
    }
}

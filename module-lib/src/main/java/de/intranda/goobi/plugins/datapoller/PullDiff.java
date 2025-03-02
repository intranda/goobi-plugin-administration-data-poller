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
package de.intranda.goobi.plugins.datapoller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import de.intranda.goobi.plugins.datapoller.xls.XlsData;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

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
        reset(processId, processTitle, failed, debugMessage);
    }

    public void reset(Integer processId, String processTitle, boolean failed, String debugMessage) {
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

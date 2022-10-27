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
package de.intranda.goobi.plugins.cataloguePoller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.intranda.goobi.plugins.cataloguePoller.xls.XlsData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Person;

@Data
@Log4j2
public class PollDocStruct {
    private List<PullMetadataType> types;
    private List<PullPersonType> personTypes;
    private List<PullGroup> groupTypes;

    /**
     * constructor to fill this data model with a given ugh docstruct
     * 
     * @param inStruct
     */
    public PollDocStruct(DocStruct inStruct) {
        types = new ArrayList<>();
        personTypes = new ArrayList<>();
        groupTypes = new ArrayList<>();
        if (inStruct.getAllMetadata() != null) {
            for (Metadata md : inStruct.getAllMetadata()) {
                addMetadata(md.getType().getName(), md.getValue());
            }
        }
        if (inStruct.getAllPersons() != null) {
            for (Person p : inStruct.getAllPersons()) {
                addPerson(p.getType().getName(), p.getFirstname(), p.getLastname(), p.getAuthorityURI(), p.getAuthorityValue());
            }
        }

        if (inStruct.getAllMetadataGroups() != null) {
            for (MetadataGroup mg : inStruct.getAllMetadataGroups()) {
                addGroup(mg);
            }
        }

    }

    /**
     * add a metadata value to the list of a specific type
     * 
     * @param title
     * @param value
     */
    private void addMetadata(String title, String value) {
        getPullMetadataTypeByTitle(title).addValue(value);
    }

    /**
     * add a person to the list of a specific person role
     * 
     * @param title
     * @param value
     */
    private void addPerson(String role, String firstName, String lastName, String authorityUrl, String authorityValue) {
        getPullPersonTypeByRole(role).addPerson(firstName, lastName, authorityUrl, authorityValue);
    }

    /**
     * add a metadata group to the list
     * 
     * @param group
     */

    private void addGroup(MetadataGroup group) {
        PullGroup grp = new PullGroup(group.getType().getName());
        StringBuilder metadata = new StringBuilder();
        for (Metadata md : group.getMetadataList()) {
            metadata.append(md.getType().getName());
            metadata.append(": ");
            metadata.append(md.getValue());
            metadata.append(": ");
        }
        grp.addMetadataToGroup(metadata.toString());

    }

    /**
     * find the right metadata type, if not there create it now
     * 
     * @param title
     * @return
     */
    public PullMetadataType getPullMetadataTypeByTitle(String title) {
        for (PullMetadataType p : types) {
            if (p.getTitle().equals(title)) {
                return p;
            }
        }
        PullMetadataType pmt = new PullMetadataType(title);
        types.add(pmt);
        return pmt;
    }

    /**
     * find the right person role, if not there create it now
     * 
     * @param role
     * @return
     */
    public PullPersonType getPullPersonTypeByRole(String role) {
        for (PullPersonType p : personTypes) {
            if (p.getRole().equals(role)) {
                return p;
            }
        }
        PullPersonType ppt = new PullPersonType(role);
        personTypes.add(ppt);
        return ppt;
    }

    public PullGroup getPullGroupByType(String type) {

        for (PullGroup pg : groupTypes) {
            if (pg.getGroupType().equals(type)) {
                return pg;
            }
        }
        PullGroup grp = new PullGroup(type);
        groupTypes.add(grp);
        return grp;

    }

    /**
     * Compare the metadata and persons of the two docstructs
     * 
     * @param topstructNew
     * @param topstructOld
     * @return
     */
    public static void checkDifferences(DocStruct topstructNew, DocStruct topstructOld, List<String> fieldFilterList, PullDiff differences,
            boolean isBlockList) {

        PollDocStruct pdsOld = new PollDocStruct(topstructOld);
        PollDocStruct pdsNew = new PollDocStruct(topstructNew);
        // run through the list of metadata fields

        // first collect all available metadata types in old and new record
        Set<String> allTypes = new HashSet<>();
        for (PullMetadataType pmtNew : pdsNew.getTypes()) {
            allTypes.add(pmtNew.getTitle());
        }
        for (PullMetadataType pmtOld : pdsOld.getTypes()) {
            allTypes.add(pmtOld.getTitle());
        }

        for (String oneType : allTypes) {
            //            log.debug("check metadata type: " + oneType);
            boolean handleMetadata = fieldFilterList.contains(oneType);
            // if the list is a blackList the behaviour shall be inversed
            handleMetadata = (isBlockList) ? !handleMetadata : handleMetadata;
            if (handleMetadata) {
                PullMetadataType pmtNew = pdsNew.getPullMetadataTypeByTitle(oneType);
                PullMetadataType pmtOld = pdsOld.getPullMetadataTypeByTitle(oneType);
                if (pmtNew.getValues().size() != pmtOld.getValues().size()) {
                    // number of metadata fields is different

                    String helperOldValues = "";
                    for (String value : pmtOld.getValues()) {
                        helperOldValues += value + "; ";
                    }
                    String helperNewValues = "";
                    for (String value : pmtNew.getValues()) {
                        helperNewValues += value + "; ";
                    }
                    differences.getMessages()
                            .add(pmtNew.getTitle() + ": Number of old values (" + pmtOld.getValues().size() + ") is different from new values ("
                                    + pmtNew.getValues().size() + ") <br/>[Old values: " + helperOldValues + " => New values: " + helperNewValues
                                    + "]");
                    differences.getXlsData().add(new XlsData(pmtNew.getTitle(), helperOldValues, helperNewValues));
                } else {
                    // number of metadata fields is the same
                    for (String value : pmtNew.getValues()) {
                        if (!pmtOld.getValues().contains(value)) {
                            differences.getMessages().add(pmtNew.getTitle() + ": New metadata value '" + value + "' found.");
                            differences.getXlsData().add(new XlsData(pmtNew.getTitle(), "", value));
                        } else {
                            // remove all fields from old list
                            pmtOld.getValues().remove(value);
                        }
                    }
                    // if there are still fiels in the old list then these were
                    // not in the new list
                    if (pmtOld.getValues().size() > 0) {
                        for (String value : pmtOld.getValues()) {
                            differences.getMessages().add(pmtOld.getTitle() + ": Old value '" + value + "' was not in the new record anymore.");
                            differences.getXlsData().add(new XlsData(pmtNew.getTitle(), value, ""));
                        }
                    }
                }
            }
        }

        // then collect all available person types in old and new record
        Set<String> allPersonTypes = new HashSet<>();
        for (PullPersonType pptNew : pdsNew.getPersonTypes()) {
            allPersonTypes.add(pptNew.getRole());
        }
        for (PullPersonType pptOld : pdsOld.getPersonTypes()) {
            allPersonTypes.add(pptOld.getRole());
        }

        // run through all persons
        for (String ppt : allPersonTypes) {
            boolean handlePerson = fieldFilterList.contains(ppt);
            // if the list is a blackList the behaviour shall be inversed
            handlePerson = (isBlockList) ? !handlePerson : handlePerson;
            if (handlePerson) {
                PullPersonType pptNew = pdsNew.getPullPersonTypeByRole(ppt);
                PullPersonType pptOld = pdsOld.getPullPersonTypeByRole(ppt);
                if (pptNew.getPersons().size() != pptOld.getPersons().size()) {
                    // number of person fields is different
                    String helperOldPersons = "";
                    for (PullPerson pp : pptOld.getPersons()) {
                        helperOldPersons += pp.getLastName() + ", " + pp.getFirstName();
                        if (pp.getAuthorityUrl() != null || pp.getAuthorityValue() != null) {
                            helperOldPersons += " (";
                            if (pp.getAuthorityUrl() != null) {
                                helperOldPersons += pp.getAuthorityUrl();
                            }
                            if (pp.getAuthorityUrl() != null && pp.getAuthorityValue() != null) {
                                helperOldPersons += ": ";
                            }
                            if (pp.getAuthorityValue() != null) {
                                helperOldPersons += pp.getAuthorityValue();
                            }
                            helperOldPersons += ")";
                        }
                        helperOldPersons += "; ";
                    }
                    String helperNewPersons = "";
                    for (PullPerson pp : pptNew.getPersons()) {
                        helperNewPersons += pp.getLastName() + ", " + pp.getFirstName();
                        if (pp.getAuthorityUrl() != null || pp.getAuthorityValue() != null) {
                            helperNewPersons += " (";
                            if (pp.getAuthorityUrl() != null) {
                                helperNewPersons += pp.getAuthorityUrl();
                            }
                            if (pp.getAuthorityUrl() != null && pp.getAuthorityValue() != null) {
                                helperNewPersons += ": ";
                            }
                            if (pp.getAuthorityValue() != null) {
                                helperNewPersons += pp.getAuthorityValue();
                            }
                            helperNewPersons += ")";
                        }
                        helperNewPersons += "; ";
                    }
                    differences.getMessages()
                            .add(pptNew.getRole() + ": Number of old persons (" + pptOld.getPersons().size() + ") is different from new persons ("
                                    + pptNew.getPersons().size() + ") <br/>[Old persons: " + helperOldPersons + " => New persons: " + helperNewPersons
                                    + "]");
                    differences.getXlsData().add(new XlsData(pptNew.getRole(), helperOldPersons, helperNewPersons));
                } else {
                    // number of person fields is the same
                    for (PullPerson pp : pptNew.getPersons()) {
                        if (!pptOld.getPersons().contains(pp)) {
                            differences.getMessages()
                                    .add(pptNew.getRole() + ": New person '" + pp.getLastName() + ", " + pp.getFirstName() + " ("
                                            + pp.getAuthorityUrl() + ": " + pp.getAuthorityValue() + ")' found.");
                            differences.getXlsData().add(new XlsData(pptNew.getRole(), "", pp.getLastName() + ", " + pp.getFirstName()));
                        } else {
                            // remove all fields from old list
                            pptOld.getPersons().remove(pp);
                        }
                    }
                    // if there are still fiels in the old list then these were
                    // not in the new list
                    if (pptOld.getPersons().size() > 0) {
                        for (PullPerson pp : pptOld.getPersons()) {
                            differences.getMessages()
                                    .add(pptOld.getRole() + ": Old value '" + pp.getLastName() + ", " + pp.getFirstName() + " ("
                                            + pp.getAuthorityUrl() + ": " + pp.getAuthorityValue() + ")' was not in the new record anymore.");
                            differences.getXlsData().add(new XlsData(pptOld.getRole(), "", pp.getLastName() + ", " + pp.getFirstName()));
                        }
                    }
                }
            }
        }

        // then collect all available group types in old and new record
        Set<String> allGroupTypes = new HashSet<>();
        for (PullGroup pgtNew : pdsNew.getGroupTypes()) {
            allGroupTypes.add(pgtNew.getGroupType());
        }
        for (PullGroup pgtOld : pdsOld.getGroupTypes()) {
            allGroupTypes.add(pgtOld.getGroupType());
        }

        // check new groups
        for (String type : allGroupTypes) {
            boolean handleGroup = fieldFilterList.contains(type);
            // if the list is a blackList the behaviour shall be inversed
            handleGroup = (isBlockList) ? !handleGroup : handleGroup;
            if (handleGroup) {
                // find old groups of the type
                PullGroup newGroup = pdsNew.getPullGroupByType(type);
                PullGroup oldGroup = pdsOld.getPullGroupByType(type);
                if (newGroup.getMetadataHashs().size() != oldGroup.getMetadataHashs().size()) {
                    // if the sizes of the groups are different

                    String helperOldGroups = "";
                    for (String value : oldGroup.getMetadataHashs()) {
                        helperOldGroups += value + "; ";
                    }
                    String helperNewGroups = "";
                    for (String value : newGroup.getMetadataHashs()) {
                        helperNewGroups += value + "; ";
                    }

                    differences.getMessages()
                            .add(newGroup.getGroupType() + ": Number of metadata in old groups (" + oldGroup.getMetadataHashs().size()
                                    + ") is different from new groups (" + newGroup.getMetadataHashs().size() + ") <br/>[Old values: "
                                    + helperOldGroups + " => New values: " + helperNewGroups + "]");
                    differences.getXlsData().add(new XlsData(newGroup.getGroupType(), helperOldGroups, helperNewGroups));
                } else {
                    for (String metadata : newGroup.getMetadataHashs()) {
                        if (!oldGroup.getMetadataHashs().contains(metadata)) {
                            differences.getMessages().add(newGroup.getGroupType() + ": New group '" + metadata + "' found.");
                            differences.getXlsData().add(new XlsData(newGroup.getGroupType(), "", metadata));
                        } else {
                            oldGroup.getMetadataHashs().remove(metadata);
                        }
                    }

                    // if there are still fiels in the old list then these were
                    // not in the new list
                    if (oldGroup.getMetadataHashs().size() > 0) {
                        for (String pp : oldGroup.getMetadataHashs()) {
                            differences.getMessages().add(oldGroup.getGroupType() + ": Old value '" + pp + "' was not in the new record anymore.");
                            differences.getXlsData().add(new XlsData(oldGroup.getGroupType(), pp, ""));
                        }
                    }
                }
            }
        }
    }

    /**
     * embedded class to manage metadata types
     */
    @Data
    public class PullMetadataType {
        private String title;
        private Set<String> values;

        public PullMetadataType(String title) {
            this.title = title;
            values = new HashSet<>();
        }

        public void addValue(String value) {
            values.add(value);
        }
    }

    /**
     * embedded class to manage person types
     */
    @Data
    public class PullPersonType {
        private String role;
        private Set<PullPerson> persons;

        public PullPersonType(String role) {
            this.role = role;
            persons = new HashSet<>();
        }

        public void addPerson(String firstName, String lastName, String authorityUrl, String authorityValue) {
            PullPerson pp = new PullPerson(firstName, lastName, authorityUrl, authorityValue);
            persons.add(pp);
        }
    }

    /**
     * embedded class to define a person
     */
    @Data
    @AllArgsConstructor
    public class PullPerson {
        private String firstName;
        private String lastName;
        private String authorityUrl;
        private String authorityValue;
    }

    @Data
    public class PullGroup {
        private String groupType;
        private List<String> metadataHashs;

        public PullGroup(String groupType) {
            this.groupType = groupType;
            metadataHashs = new ArrayList<>();
        }

        public void addMetadataToGroup(String metadata) {
            metadataHashs.add(metadata);
        }
    }
}

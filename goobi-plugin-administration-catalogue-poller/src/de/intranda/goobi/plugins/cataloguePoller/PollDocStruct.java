package de.intranda.goobi.plugins.cataloguePoller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.Person;

@Data
public class PollDocStruct {
    private List<PullMetadataType> types;
    private List<PullPersonType> personTypes;
    
    /**
     * constructor to fill this data model with a given ugh docstruct
     * @param inStruct
     */
    public PollDocStruct(DocStruct inStruct) {
        types = new ArrayList<PullMetadataType>();
        personTypes = new ArrayList<PullPersonType>();
        for (Metadata md : inStruct.getAllMetadata()) {
            addMetadata(md.getType().getName(),md.getValue());
        }
        for (Person p : inStruct.getAllPersons()) {
            addPerson(p.getType().getName(), p.getFirstname(), p.getLastname(), p.getAuthorityURI(), p.getAuthorityValue());
        }
    }

    /**
     * add a metadata value to the list of a specific type
     * @param title
     * @param value
     */
    private void addMetadata(String title, String value) {
        getPullMetadataTypeByTitle(title).addValue(value);
    }

    /**
     * add a person to the list of a specific person role
     * @param title
     * @param value
     */
    private void addPerson(String role, String firstName, String lastName, String authorityUrl, String authorityValue) {
        getPullPersonTypeByRole(role).addPerson(firstName, lastName, authorityUrl, authorityValue);
    }
    
    
    /**
     * find the right metadata type, if not there create it now
     * @param title
     * @return
     */
    public PullMetadataType getPullMetadataTypeByTitle(String title) {
        for (PullMetadataType p : types) {
            if(p.getTitle().equals(title)) {
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
            if(p.getRole().equals(role)) {
                return p;
            }
        }
        PullPersonType ppt = new PullPersonType(role);
        personTypes.add(ppt);
        return ppt;
    }
    
    /**
     * Compare the metadata and persons of the two docstructs 
     * 
     * @param topstructNew
     * @param topstructOld
     * @return
     */
    public static PullDiff checkDifferences(DocStruct topstructNew, DocStruct topstructOld,
            List<String> configSkipFields) {
        PullDiff differences = new PullDiff();
        differences.setMessages(new ArrayList<String>());
        
        PollDocStruct pdsOld = new PollDocStruct(topstructOld);
        PollDocStruct pdsNew = new PollDocStruct(topstructNew);
        // run through the list of metadata fields
        for (PullMetadataType pmtNew : pdsNew.getTypes()) {
            if (!configSkipFields.contains(pmtNew.getTitle())) {
                PullMetadataType pmtOld = pdsOld.getPullMetadataTypeByTitle(pmtNew.getTitle());
                if (pmtNew.getValues().size() != pmtOld.getValues().size()) {
                    // number of metadata fields is different
                    differences.getMessages().add(pmtNew.getTitle() + ": Number of old values (" + pmtOld.getValues().size()
                            + ") is different from new values (" + pmtNew.getValues().size() + ")");
                } else {
                    // number of metadata fields is the same
                    for (String value : pmtNew.getValues()) {
                        if (!pmtOld.getValues().contains(value)) {
                            differences.getMessages().add(pmtNew.getTitle() + ": New metadata value '" + value + "' found.");
                        } else {
                            // remove all fields from old list
                            pmtOld.getValues().remove(value);
                        }
                    }
                    // if there are still fiels in the old list then these were
                    // not in the new list
                    if (pmtOld.getValues().size() > 0) {
                        for (String value : pmtOld.getValues()) {
                            differences.getMessages().add(pmtOld.getTitle() + ": Old value '" + value
                                    + "' was not in the new record anymore.");
                        }
                    }
                }
            }
        }
        
        // run through all persons
        for (PullPersonType pptNew : pdsNew.getPersonTypes()) {
            if (!configSkipFields.contains(pptNew.getRole())) {
                PullPersonType pptOld = pdsOld.getPullPersonTypeByRole(pptNew.getRole());
                if (pptNew.getPersons().size() != pptOld.getPersons().size()) {
                    // number of person fields is different
                    differences.getMessages().add(pptNew.getRole() + ": Number of old persons (" + pptOld.getPersons().size()
                            + ") is different from new persons (" + pptNew.getPersons().size() + ")");
                } else {
                    // number of person fields is the same
                    for (PullPerson pp : pptNew.getPersons()) {
                        if (!pptOld.getPersons().contains(pp)) {
                            differences.getMessages().add(pptNew.getRole() + ": New person '" + pp.getLastName() + ", " + pp.getFirstName() + " (" + pp.getAuthorityUrl() + ": " + pp.getAuthorityValue() + ")' found.");
                        } else {
                            // remove all fields from old list
                            pptOld.getPersons().remove(pp);
                        }
                    }
                    // if there are still fiels in the old list then these were
                    // not in the new list
                    if (pptOld.getPersons().size() > 0) {
                        for (PullPerson pp : pptOld.getPersons()) {
                            differences.getMessages().add(pptOld.getRole() + ": Old value '" + pp.getLastName() + ", " + pp.getFirstName() + " (" + pp.getAuthorityUrl() + ": " + pp.getAuthorityValue() + ")' was not in the new record anymore.");
                        }
                    }
                }
            }
        }
        return differences;
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
            values = new HashSet<String>();
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
            persons = new HashSet<PullPerson>();
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
    
    /**
     * embedded class for the differences
     */
    @Data
    public static class PullDiff {
        private Integer processId;
        private String processTitle;
        private List<String> messages;
    }
}

package de.intranda.goobi.plugins.cataloguePoller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct;
import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;

public class PollDocStructTest {
    public static DigitalDocument document;
    public static DocStruct dsOld;
    public static DocStruct dsNew;
    public static Path ruleSet = Paths.get("target/test-classes/ruleset.xml");
    public static Prefs prefs;

    @BeforeClass
    public static void setup() throws MetadataTypeNotAllowedException {
        document = new DigitalDocument();
        prefs = new Prefs();
        try {
            prefs.loadPrefs(ruleSet.toString());
        } catch (PreferencesException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        resetDocStructs();
    }

    @Test
    public void testcheckDifferences() {
        //prepare test
        List<String> filter = new ArrayList<>();
        filter.add("PublicationYear");
        filter.add("Author");
        addMetadata(dsNew, "PublicationYear", "1970");
        boolean isBlockList = false;
        PullDiff diff = new PullDiff();
        PollDocStruct.checkDifferences(dsNew, dsOld, filter, diff, isBlockList);

        // one entry for the new metadata element
        Assert.assertEquals("No Message was genearted for the missing publication year", 1, diff.getMessages().size());
        Assert.assertEquals("No xlsx-entry was generated for the missing publication year", 1, diff.getXlsData().size());
        addMetadata(dsOld, "PublicationYear", "1975");
        PullDiff diff2 = new PullDiff();

        // one entry for the old value and one entry for the new value
        PollDocStruct.checkDifferences(dsNew, dsOld, filter, diff2, isBlockList);
        Assert.assertEquals("Two Messages should have been created, comparing the docstructs", 2, diff2.getMessages().size());
        Assert.assertEquals("Two xlsx-entries should have been created comparing the docstructs", 2, diff2.getXlsData().size());

        // reset dsOld and dsNew
        resetDocStructs();
        addPerson(dsNew, "Author", "Noam", "Chomsky");
        PullDiff diff3 = new PullDiff();
        PollDocStruct.checkDifferences(dsNew, dsOld, filter, diff3, isBlockList);

        // one entry for the new person element
        Assert.assertEquals("One message should have been created for the new person element", 1, diff3.getMessages().size());
        Assert.assertEquals("One xlsx-entry should have been created for the new person element", 1, diff3.getXlsData().size());

        //test blacklist
        PullDiff diff4 = new PullDiff();
        addPerson(dsOld, "Author", "Stephen", "King");
        PollDocStruct.checkDifferences(dsNew, dsOld, filter, diff4, true);
        Assert.assertEquals("The List should be empty because the elements are black listetd", 0, diff4.getXlsData().size());

        // differing person elements should be summarized in one message
        PollDocStruct.checkDifferences(dsNew, dsOld, filter, diff4, isBlockList);
        Assert.assertEquals("Two messages should have been created", 2, diff4.getMessages().size());
        Assert.assertEquals("Two xlsx-entries should have been created", 2, diff4.getXlsData().size());
    }

    private static void addMetadata(DocStruct ds, String type, String value) {
        try {
            Metadata metadata = new Metadata(prefs.getMetadataTypeByName(type));
            metadata.setValue(value);
            ds.addMetadata(metadata);
        } catch (MetadataTypeNotAllowedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void addPerson(DocStruct ds, String type, String firstName, String lastName) {
        try {
            Person person = new Person(prefs.getMetadataTypeByName(type), firstName, lastName);
            ds.addPerson(person);
        } catch (MetadataTypeNotAllowedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void resetDocStructs() {
        DocStructType type = prefs.getDocStrctTypeByName("Monograph");
        try {
            dsOld = document.createDocStruct(type);
            dsNew = document.createDocStruct(type);
        } catch (TypeNotAllowedForParentException e) {
            System.out.println("Error setting docstruct");
            e.printStackTrace();
        }
    }

}

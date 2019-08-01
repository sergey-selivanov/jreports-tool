package sssii.jreports.test;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import sssii.jreports.ImportGenerator;
import sssii.jreports.ToolException;

public class ImportGeneratorTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testProcess() {

        try {
            ImportGenerator gen = new ImportGenerator();

            gen.process(
                    new String[] {"d:/git/nnn-reports-nnngit", "d:/git/nnn-reports-annual"},
                    "build/targetdir",
                        //"d:\\tmp",
                    "build/importReports.zip",
                    "/reports/permits2",
                    "test_ds",
                    "org.mariadb.jdbc.Driver",
                    "jdbc:mysql://localhost:3306/permits",
                    "permits",
                    "permits",
                    "src/main/resources/sampleExcludes.txt"
                    );
        } catch (ToolException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

}

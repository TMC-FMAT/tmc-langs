package fi.helsinki.cs.tmc.langs.ant;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import fi.helsinki.cs.tmc.langs.ExerciseDesc;
import fi.helsinki.cs.tmc.langs.LanguagePlugin;
import fi.helsinki.cs.tmc.langs.RunResult.Status;
import fi.helsinki.cs.tmc.langs.TestDesc;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class AntPluginTest {

    private AntPlugin antPlugin;

    public AntPluginTest() {
        antPlugin = new AntPlugin();
    }

    @Test
    public void testGetLanguageName() {
        assertEquals("apache-ant", antPlugin.getLanguageName());
    }

    @Test
    public void scanExerciseReturnExerciseDesc() {
        String name = "Ant Test";
        ExerciseDesc description = antPlugin.scanExercise(getPath("ant_project"), name);
        assertEquals(name, description.name);
        assertEquals(3, description.tests.size());
    }

    @Test
    public void scanExerciseReturnsCorrectTests() {
        ExerciseDesc description = antPlugin.scanExercise(getPath("ant_project"), "AntTestSubject");
        assertEquals(3, description.tests.size());

        TestDesc test = description.tests.get(0);
        assertEquals("AntTestSubject oneExTestMethod", test.name);
        assertEquals("one", test.points.get(0));

        test = description.tests.get(2);
        assertEquals("AntTestSubject twoExTestMethod", test.name);
        assertEquals("one", test.points.get(0));
        assertEquals("two", test.points.get(1));
    }

    @Test
    public void scanExerciseReturnsNullWhenWrongProjectType() {
        assertNull(antPlugin.scanExercise(getPath("non_ant_project"), "Dummy"));
    }
    
    @Test
    public void testRunnerArgsGetsCreatedCorrectly() {
        antPlugin.runTests(getPath("ant_project"));
    }

    private Path getPath(String location) {
        return Paths.get("src/test/resources/" + location);
    }

}

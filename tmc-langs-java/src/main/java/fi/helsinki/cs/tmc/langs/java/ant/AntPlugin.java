package fi.helsinki.cs.tmc.langs.java.ant;

import fi.helsinki.cs.tmc.langs.domain.CompileResult;
import fi.helsinki.cs.tmc.langs.domain.ExerciseDesc;
import fi.helsinki.cs.tmc.langs.io.StudentFilePolicy;
import fi.helsinki.cs.tmc.langs.io.sandbox.StudentFileAwareSubmissionProcessor;
import fi.helsinki.cs.tmc.langs.java.AbstractJavaPlugin;
import fi.helsinki.cs.tmc.langs.java.ClassPath;
import fi.helsinki.cs.tmc.langs.java.exception.TestRunnerException;
import fi.helsinki.cs.tmc.langs.java.exception.TestScannerException;
import fi.helsinki.cs.tmc.langs.java.testscanner.TestScanner;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * A {@link fi.helsinki.cs.tmc.langs.LanguagePlugin} that defines the behaviour for Java projects
 * that use Apache Ant.
 */
public class AntPlugin extends AbstractJavaPlugin {

    private static final Path TEST_DIR = Paths.get("test");
    private static final Path RESULT_FILE = Paths.get("results.txt");
    private static final Path BUILD_FILE = Paths.get("build.xml");
    private static final Path BUILD_LOG_FILE = Paths.get("build_log.txt");
    private static final Path BUILD_ERROR_LOG_FILE = Paths.get("build_errors.txt");

    private static final String ANT_BUILD_FILE_PROPERTY = "ant.file";
    private static final String ANT_JAVAC_FORK_PROPERTY = "javac.fork";
    private static final String ANT_JAVAC_FORK_VALUE = "true";
    private static final String ANT_PROJECT_HELPER_PROPERTY = "ant.projectHelper";
    private static final String ANT_COMPILE_TEST_TARGET = "compile-test";

    private static final int STATUS_CODE_SUCCESS = 0;
    private static final int STATUS_CODE_ERROR = 1;

    private static final Logger log = LoggerFactory.getLogger(AntPlugin.class);

    /**
     * Create a new AntPlugin.
     */
    public AntPlugin() {
        super(TEST_DIR, new StudentFileAwareSubmissionProcessor(), new TestScanner());
    }

    @Override
    public String getLanguageName() {
        return "apache-ant";
    }

    @Override
    public boolean isExerciseTypeCorrect(Path path) {
        return Files.exists(path.resolve(BUILD_FILE));
    }

    @Override
    protected StudentFilePolicy getStudentFilePolicy(Path path) {
        return new AntStudentFilePolicy(path);
    }

    /**
     * Runs the build.xml file for the the given exercise.
     *
     * @param path The file path of the exercise directory.
     * @return true if build success, else return false.
     */
    @Override
    protected CompileResult build(Path path) {

        log.info("Building project at {}", path);

        File buildFile = path.resolve(BUILD_FILE).toFile();
        Project buildProject = new Project();
        buildProject.setUserProperty(ANT_BUILD_FILE_PROPERTY, buildFile.getAbsolutePath());
        buildProject.setProperty(ANT_JAVAC_FORK_PROPERTY, ANT_JAVAC_FORK_VALUE);
        buildProject.init();
        buildProject.setBaseDir(path.toAbsolutePath().toFile());

        File buildLog = path.resolve(BUILD_LOG_FILE).toFile();
        File errorLog = path.resolve(BUILD_ERROR_LOG_FILE).toFile();

        DefaultLogger logger = new DefaultLogger();
        try {

            PrintStream stdOut = new PrintStream(buildLog);
            PrintStream stdErr = new PrintStream(errorLog);

            logger.setErrorPrintStream(stdErr);
            logger.setOutputPrintStream(stdOut);
            logger.setMessageOutputLevel(Project.MSG_INFO);

            buildProject.addBuildListener(logger);
            buildProject.fireBuildStarted();
            ProjectHelper helper = ProjectHelper.getProjectHelper();
            buildProject.addReference(ANT_PROJECT_HELPER_PROPERTY, helper);
            helper.parse(buildProject, buildFile);
            buildProject.executeTarget(ANT_COMPILE_TEST_TARGET);
            buildProject.fireBuildFinished(null);

            log.info("Successfully built project at {}", path);

            return new CompileResult(STATUS_CODE_SUCCESS,
                    Files.readAllBytes(buildLog.toPath()),
                    Files.readAllBytes(errorLog.toPath()));

        } catch (BuildException buildException) {
            log.info("Error building project at {}", path, buildException);
            try {
                buildProject.fireBuildFinished(buildException);

                return new CompileResult(STATUS_CODE_ERROR,
                        Files.readAllBytes(buildLog.toPath()),
                        Files.readAllBytes(errorLog.toPath()));
            } catch (IOException ioException) {
                log.error("Unable to fire build finisher", ioException);
                throw Throwables.propagate(buildException);
            }

        } catch (IOException ioException) {
            log.info("Error building project at {}", path, ioException);
            throw Throwables.propagate(ioException);
        }
    }


    @Override
    protected ClassPath getProjectClassPath(Path path) {
        ClassPath classPath = new ClassPath(path.toAbsolutePath());
        classPath.addDirAndContents(path.resolve("lib"));
        classPath.add(path.resolve(Paths.get("build", "test", "classes")));
        classPath.add(path.resolve(Paths.get("build", "classes")));

        return classPath;
    }

    @Override
    protected File createRunResultFile(Path projectBasePath)
            throws TestRunnerException, TestScannerException {

        log.info("Running tests for project at {}", projectBasePath);

        Optional<ExerciseDesc> exercise = scanExercise(projectBasePath,
                                                       projectBasePath.toString() + TEST_DIR);
        if (!exercise.isPresent()) {
            log.error("Unable to create run result file due to absent ExerciseDesc");
            throw new TestScannerException();
        }

        Path testDir = projectBasePath.resolve(TEST_DIR);
        Path resultFile = projectBasePath.resolve(RESULT_FILE);
        ClassPath classPath = getProjectClassPath(projectBasePath);
        TestRunnerArgumentBuilder argumentBuilder =  new TestRunnerArgumentBuilder(
                projectBasePath,
                testDir,
                resultFile,
                classPath,
                exercise.get());
        List<String> testRunnerArguments = argumentBuilder.getArguments();

        try {
            Process process = new ProcessBuilder(testRunnerArguments).start();
            process.waitFor();
        } catch (InterruptedException | IOException e) {
            log.error("Failed to run tests", e);
            throw new TestRunnerException(e);
        }

        log.info("Successfully ran tests for project at {}", projectBasePath);

        return resultFile.toFile();
    }
}
package net.java.mavenincrementalbuild;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class IncrementalBuildMojoTest {
    private static final String TARGET_TEST_SOURCE = "target/test/source";
    private static final String TARGET_TEST_OUTPUT = "target/test/output";
    private static final String TARGET_TEST_TEST_SOURCE = "target/test/test";
    private static final String TARGET_TEST_TEST_OUTPUT = "target/test/output-test";
    private static final String TARGET_TEST_RESOURCES = "target/test/resources";
    private static final String TARGET_TEST_TEST_RESOURCES = "target/test/test-resources";
    private IncrementalBuildMojo mojo;
    private File resourcesDir;
    private File resourceFile1;
    /** The build target dir */ 
    private File targetDir;
    private File outputResourceFile1;
    private File resourceFile2;
    private File outputResourceFile2;
    private File sourcesDir;
    private File sourceFile1;
    private File outputSourceFile1;
    private File testsDir;
    private File testFile1;
    private File testTargetDir;
    private File outputTestFile1;
    private File testResourcesDir;
    private File testResourceFile1;
    private File outputTestResourceFile1;
            

    @Before
    public void init() throws IOException {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);

        build.setOutputDirectory(TARGET_TEST_OUTPUT);
        build.setSourceDirectory(TARGET_TEST_SOURCE);

        List resources = new ArrayList();
        Resource resource = new Resource();
        resource.setDirectory(TARGET_TEST_RESOURCES);
        resources.add(resource);
        build.setResources(resources);

        // Test elements
        build.setTestSourceDirectory(TARGET_TEST_TEST_SOURCE);
        List testResources = new ArrayList();
        Resource testResource = new Resource();
        testResource.setDirectory(TARGET_TEST_TEST_RESOURCES);
        testResources.add(testResource);

        build.setTestOutputDirectory(TARGET_TEST_TEST_OUTPUT);

        build.setTestResources(testResources);

        MavenProject project = new MavenProject(model);

        mojo = new IncrementalBuildMojo();
        mojo.setProject(project);
        mojo.setTargetDirectory(TARGET_TEST_OUTPUT);

        Calendar pastDate = Calendar.getInstance();
        pastDate.add(Calendar.HOUR, -1);

        resourcesDir = createDirectory(TARGET_TEST_RESOURCES);
        resourceFile1 = createNewFile(resourcesDir, "file1");
        resourceFile1.setLastModified(pastDate.getTimeInMillis());
        resourceFile2 = createNewFile(resourcesDir, "file2");
        resourceFile2.setLastModified(pastDate.getTimeInMillis());

        targetDir = createDirectory(TARGET_TEST_OUTPUT);
        outputResourceFile1 = createNewFile(targetDir, "file1");
        outputResourceFile2 = createNewFile(targetDir, "file2");

        sourcesDir = createDirectory(TARGET_TEST_SOURCE);
        sourceFile1 = createNewFile(sourcesDir, "sourcefile1.java");
        outputSourceFile1 = createNewFile(targetDir, "sourceFile1.class");
        
        testsDir = createDirectory(TARGET_TEST_TEST_SOURCE);
        testFile1 = createNewFile(testsDir, "testFile1.java");

        testTargetDir = createDirectory(TARGET_TEST_TEST_OUTPUT);
        outputTestFile1 = createNewFile(testTargetDir, "testFile1.class");

        testResourcesDir = createDirectory(TARGET_TEST_TEST_RESOURCES);
        testResourceFile1 = createNewFile(testResourcesDir, "testresource1");
        testResourceFile1.setLastModified(pastDate.getTimeInMillis());
        outputTestResourceFile1 = createNewFile(testTargetDir, "testresource1");

        assertFalse("No modifications must be detected at this step", mojo.resourcesUpdated());
        assertFalse("No modifications must be detected at this step", mojo.sourcesUpdated());
        assertFalse("No modifications must be detected at this step", mojo.testsUpdated());
    }

    @Test
    public void testOutputResourceDeletedDetected() throws IOException {
        outputResourceFile2.delete();
        assertTrue("Deleted output file not detected", mojo.resourcesUpdated());
    }

    @Test
    public void testNewResourceDetected() throws IOException {
        File sourceFile3 = new File(resourcesDir, "file3");
        sourceFile3.createNewFile();
        
        boolean resourcesModified = mojo.resourcesUpdated();
        assertTrue("New file not detected", resourcesModified);
    }
    
    @Test
    public void testNoModificationDetected() throws IOException {
        boolean resourceModified = mojo.resourcesUpdated();
        assertFalse("Second pass, no modification must be detected", resourceModified);
    }

    @Test
    public void testResourceDeletionDetected() throws IOException {

        resourceFile2.delete();

        boolean resourceModifier = mojo.resourcesUpdated();
        assertTrue("Resource deletion not detected", resourceModifier);
    }

    @Test
    public void testSourceFileDeletionDetected() throws IOException {
        sourceFile1.delete();
        assertTrue("source deletion not detected", mojo.sourcesUpdated());
    }
    
    @Test
    public void testTestSourcesDeletionDetected() throws IOException {
        testFile1.delete();
        assertTrue("Test source deletion not detected", mojo.testsUpdated());
    }

    private File createDirectory(String path) throws IOException {
        File file = new File(path);
        FileUtils.deleteDirectory(file);
        file.mkdirs();
        return file;
    }
    
    private File createNewFile(File directory, String fileName) throws IOException {
        File file = new File(directory, fileName);
        file.createNewFile();
        return file;
    }
}


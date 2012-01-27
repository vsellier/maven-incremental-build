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
    private static final String TARGET_TEST_OUTPUT = "target/test/output";
    private static final String TARGET_TEST_RESOURCES = "target/test/resources";
    private IncrementalBuildMojo mojo;
    private File resourcesDir;
    private File sourceFile1;
    private File outputDir;
    private File outputFile1;
    private File sourceFile2;
    private File outputFile2;

    @Before
    public void init() throws IOException {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);

        build.setOutputDirectory(TARGET_TEST_OUTPUT);

        List resources = new ArrayList();
        Resource resource = new Resource();
        resource.setDirectory(TARGET_TEST_RESOURCES);
        resources.add(resource);
        build.setResources(resources);

        MavenProject project = new MavenProject(model);

        mojo = new IncrementalBuildMojo();
        mojo.setProject(project);
        mojo.setTargetDirectory(TARGET_TEST_OUTPUT);

        Calendar pastDate = Calendar.getInstance();
        pastDate.add(Calendar.HOUR, -1);

        resourcesDir = new File(TARGET_TEST_RESOURCES);
        FileUtils.deleteDirectory(resourcesDir);
        resourcesDir.mkdirs();
        sourceFile1 = new File(resourcesDir, "file1");
        sourceFile1.createNewFile();
        sourceFile1.setLastModified(pastDate.getTimeInMillis());
        sourceFile2 = new File(resourcesDir, "file2");
        sourceFile2.createNewFile();
        sourceFile2.setLastModified(pastDate.getTimeInMillis());

        outputDir = new File(TARGET_TEST_OUTPUT);
        FileUtils.deleteDirectory(outputDir);
        outputDir.mkdirs();
        outputFile1 = new File(outputDir, "file1");
        outputFile1.createNewFile();
        outputFile2 = new File(outputDir, "file2");
        outputFile2.createNewFile();

        boolean resourcesModified = mojo.resourcesUpdated();
        assertFalse("No modifications must be detected at this step", resourcesModified);
    }

    @Test
    public void testOutputResourceDeletedDetected() throws IOException {
        outputFile2.delete();
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

        sourceFile2.delete();

        boolean resourceModifier = mojo.resourcesUpdated();
        assertTrue("Resource deletion not detected", resourceModifier);
    }
}


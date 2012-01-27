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

    @Before
    public void init() throws IOException {
        File resourcesDir = new File(TARGET_TEST_RESOURCES);
        FileUtils.deleteDirectory(resourcesDir);
    }
    
    @Test
    public void testResourceDetection() throws IOException {
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

        IncrementalBuildMojo mojo = new IncrementalBuildMojo();
        mojo.setProject(project);
        mojo.setTargetDirectory(TARGET_TEST_OUTPUT);

        File resourcesDir = new File(TARGET_TEST_RESOURCES);
        resourcesDir.mkdirs();

        boolean resourceModified = mojo.resourcesUpdated();
        assertTrue("First pass, resource modification must be detected", resourceModified);

        resourceModified = mojo.resourcesUpdated();
        assertFalse("Second pass, no modification should be detected", resourceModified);
    }

    @Test
    public void testResourceDeletion() throws IOException {
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

        IncrementalBuildMojo mojo = new IncrementalBuildMojo();
        mojo.setProject(project);
        mojo.setTargetDirectory(TARGET_TEST_OUTPUT);

        File resourcesDir = new File(TARGET_TEST_RESOURCES);
        resourcesDir.mkdirs();
        File sourceFile1 = new File(resourcesDir, "file1");
        sourceFile1.createNewFile();
        Calendar pastDate = Calendar.getInstance();
        pastDate.add(Calendar.HOUR, -1);
        sourceFile1.setLastModified(pastDate.getTimeInMillis());
        File sourceFile2 = new File(resourcesDir, "file2");
        sourceFile2.createNewFile();
        sourceFile2.setLastModified(pastDate.getTimeInMillis());

        Calendar actualDate = Calendar.getInstance();

        File outputDir = new File(TARGET_TEST_OUTPUT);
        outputDir.mkdirs();
        File outputFile1 = new File(outputDir, "file1");
        outputFile1.createNewFile();
        outputFile1.setLastModified(actualDate.getTimeInMillis());
        File outputFile2 = new File(outputDir, "file2");
        outputFile2.createNewFile();
        outputFile2.setLastModified(actualDate.getTimeInMillis());

        boolean resourcesModified = mojo.resourcesUpdated();
        assertFalse("No modifications must be detected at this step", resourcesModified);

        sourceFile2.delete();

        boolean resourceModifier = mojo.resourcesUpdated();
        assertTrue("Resource deletion not detected", resourceModifier);

    }
}

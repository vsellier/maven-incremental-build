package org.jvnet.mavenincrementalbuild;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static junit.framework.Assert.assertTrue;

public class IncrementalBuildMojoTest {
    private static final String TARGET_TEST_OUTPUT = "target/test/output";
    private static final String TARGET_TEST_RESOURCES = "target/test/resources";

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

        File resourcesDir = new File(TARGET_TEST_RESOURCES);
        resourcesDir.mkdirs();
        File file1 = new File(resourcesDir, "file1");
        file1.createNewFile();
        Calendar pastDate = Calendar.getInstance();
        pastDate.add(Calendar.HOUR, -1);
        file1.setLastModified(pastDate.getTimeInMillis());

        Calendar actualDate = Calendar.getInstance();

        File outputDir = new File(TARGET_TEST_OUTPUT);
        outputDir.mkdirs();
        File file2 = new File(outputDir, "file1");
        file2.createNewFile();
        file2.setLastModified(actualDate.getTimeInMillis());
        File file3 = new File(outputDir, "file2");
        file3.createNewFile();
        file3.setLastModified(actualDate.getTimeInMillis());


        boolean resourceModifier = mojo.resourcesUpdated();
        assertTrue("Resource deletion not detected", resourceModifier);

    }
}

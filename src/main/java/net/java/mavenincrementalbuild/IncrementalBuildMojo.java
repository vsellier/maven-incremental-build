/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package net.java.mavenincrementalbuild;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.java.mavenincrementalbuild.utils.MapFileManager;
import net.java.mavenincrementalbuild.utils.SetFileManager;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * Goal which touches a timestamp file.
 */
@Mojo(name = "incremental-build", threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE, //
requiresDependencyResolution = ResolutionScope.TEST)
public class IncrementalBuildMojo extends AbstractMojo {
    private final static String TIMESTAMPS_FILE = "timestamp";
    private static final String RESOURCES_LIST_FILE = "resourcesList";
    protected static final String TEST_RESOURCES_LIST_FILE = "testResourcesList";
    private static final String SOURCE_LIST_FILE = "sourcesList";
    protected static final String TEST_LIST_FILE = "testsList";

    /**
     * The Maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /**
     * Dependencies from the reactor. This attribute is a singleton for the complete build process
     */
    private final static Map<ModuleIdentifier, Module> resolvedDependencies = new ConcurrentHashMap<ModuleIdentifier, Module>();

    /**
     * the timestamp manager
     */
    private MapFileManager<String, Long> timestampManager;

    /**
     * Set this to 'true' to deactivate the incremental build.
     *
     * @since 1.2
     */
    @Parameter(property = "noIncrementalBuild")
    private boolean noIncrementalBuild;

    /**
     * The target directory root
     */
    private String targetDirectory = null;

    public void execute() throws MojoExecutionException {
        Module module = null;

        if (noIncrementalBuild) {
            getLog().info("Incremental build deactivated.");
            return;
        }

        ModuleIdentifier moduleIdentifier = new ModuleIdentifier(project.getGroupId(), project.getArtifactId(), project
                .getVersion());

        if (resolvedDependencies.get(moduleIdentifier) != null) {
            getLog().info("Incremental build test already done. Skipping...");
            return;
        }

        targetDirectory = project.getBuild().getDirectory();

        if (getLog().isDebugEnabled()) {
            getLog().debug("Resolved modules : " + resolvedDependencies);
            getLog().debug("Loading previous timestamps ...");
        }

        try {
            timestampManager = new MapFileManager<String, Long>(getLog(), targetDirectory, TIMESTAMPS_FILE);
            timestampManager.load();
        } catch (IOException e1) {
            getLog().error("Error loading previous timestamps", e1);
            throw new MojoExecutionException("Error loading previous timestamps.", e1);
        }

        module = saveModuleState(project, moduleIdentifier, pomUpdated() || parentUpdated() || resourcesUpdated()
                || sourcesUpdated() || testsUpdated());

        if (module.isUpdated()) {
            try {
                cleanModule();
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to clean module.", e);
            }

        }

        getLog().debug("Saving timestamps..");
        try {
            timestampManager.save();
        } catch (IOException e) {
            getLog().error("Error saving timestamps.", e);
            throw new MojoExecutionException("Error saving timestamps.", e);
        }

    }

    /**
     * Clean module target directory.<br>
     * if output directories was redifine, ensure that clean will be done if
     * output and test output directories are not under build directories.
     *
     * @throws IOException
     */
    private void cleanModule() throws IOException {
        getLog().debug("Module updated, cleaning module");

        String buildDirectory = project.getBuild().getDirectory();
        String outputDirectory = project.getBuild().getOutputDirectory();
        String testOutputDirectory = project.getBuild().getTestOutputDirectory();

        deleteDirectory(buildDirectory);
        if (!outputDirectory.startsWith(buildDirectory)) {
            deleteDirectory(outputDirectory);
        }
        if (!testOutputDirectory.startsWith(buildDirectory)) {
            deleteDirectory(testOutputDirectory);
        }

    }

    private void deleteDirectory(String path) throws IOException {
        getLog().info("Deleting " + path);
        FileUtils.deleteDirectory(path);
    }

    /**
     * check if files in source directory are more recent than files on target directory.
     *
     *
     * @param sourceDirectoryPath base directory
     * @param targetDirectoryPath the generated directory
     * @return true if a file in target directory is more recent than files in source directory, false otherwise
     */
    private Boolean directoryUpdated(String listFile, String sourceDirectoryPath, String targetDirectoryPath) {
        getLog().debug("checking " + sourceDirectoryPath + " compared to " + targetDirectoryPath);

        boolean updateDetected = false;

        // Used to detect source deletion
        SetFileManager<String> previousSources = new SetFileManager<String>(getLog(), targetDirectoryPath, listFile);
        try {
            previousSources.load();
        } catch (IOException e) {
            getLog().error("Error loading previous sources file");
            return true;
        }

        SetFileManager<String> actualSources = new SetFileManager<String>(getLog(), targetDirectoryPath, listFile);

        Long lastSourceModificationDate = new Long(0);
        Long lastTargetModificationDate = new Long(0);

        File sourceDirectory = new File(sourceDirectoryPath);
        File targetDirectory = new File(targetDirectoryPath);

        if (!sourceDirectory.exists()) {
            getLog().info("No sources to check ...");
            return false;
        }

        if (!targetDirectory.exists()) {
            getLog().info("No target directory " + targetDirectoryPath + ", project already cleaned.");
            updateDetected = false;
        }

        DirectoryScanner scanner = new DirectoryScanner();
        getLog().debug("Source directory : " + sourceDirectory);
        scanner.setBasedir(sourceDirectory);
        scanner.setIncludes(new String[]{"**/*"});
        scanner.setExcludes(DirectoryScanner.DEFAULTEXCLUDES);

        getLog().debug("Scanning sources directory...");
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        getLog().debug("Source files : " + Arrays.toString(files));

        for (int i = 0; i < files.length; i++) {
            File file = new File(sourceDirectory, files[i]);
            long lastModification = file.lastModified();
            getLog().debug("" + lastModification);
            if (lastModification > lastSourceModificationDate) {
                lastSourceModificationDate = lastModification;
            }
            // Saving file into list
            actualSources.add(files[i]);
            previousSources.remove(files[i]);
        }
        getLog().debug("Last source modification : " + lastSourceModificationDate);
        // Not all previous file was found into the source directory
        // Assume some files was deleted into the source directory
        if (! previousSources.isEmpty()) {
            getLog().info("At least one source file was deleted, module have to be cleaned");
            updateDetected = true;
        }

        // Scanning target directory to compare last build date
        String targetDir = project.getBuild().getOutputDirectory();
        getLog().debug("Target directory : " + targetDir);
        if (! new File(targetDir).exists()) {
            getLog().debug("Target dir does not exist, project is already clear");
        } else {
            scanner = new DirectoryScanner();
            scanner.setBasedir(targetDir);
            scanner.setIncludes(new String[]{"**/*"});
            scanner.addDefaultExcludes();

            getLog().debug("Scanning output directory...");
            scanner.scan();
            files = scanner.getIncludedFiles();
            getLog().debug("Target files : " + Arrays.toString(files));

            // TODO put this in a method
            for (int i = 0; i < files.length; i++) {
                File file = new File(targetDir, files[i]);
                Long lastModification = file.lastModified();
                if (lastModification > lastTargetModificationDate) {
                    lastTargetModificationDate = lastModification;
                }
            }
            getLog().debug("Last target modification date : " + lastTargetModificationDate);

            if (lastSourceModificationDate > lastTargetModificationDate) {
                getLog().info("Source modification detected, clean will be called");
                updateDetected = true;
            } else {
                getLog().debug("No timestamp changes detected.");
                updateDetected |= false;
            }
        }

        getLog().debug("Saving source list");
        try {
            actualSources.save();
        } catch (IOException e) {
            getLog().warn("Error saving source files list", e);
            updateDetected = true;
        }

        return updateDetected;
    }

    /**
     * Check if modifications was done on the source folder since the last build
     *
     * @return true if modification was detected, false otherwise
     */
    protected Boolean sourcesUpdated() {
        getLog().info("Verifying sources...");

        return directoryUpdated(SOURCE_LIST_FILE, project.getBuild().getSourceDirectory(), project.getBuild().getOutputDirectory());
    }

    /**
     * Check if modification was done on the test folders
     *
     * @return true if modification was detected, false otherwise
     */
    protected Boolean testsUpdated() {
        boolean update;
        getLog().info("Verifying tests sources...");
        update = directoryUpdated(TEST_LIST_FILE, project.getBuild().getTestSourceDirectory(), project.getBuild().getTestOutputDirectory());

        getLog().info("Verifying tests resources...");
        update |= testResourcesUpdated();

        getLog().debug("test update detected : " + update);
        return update;
    }

    private Module saveModuleState(MavenProject project, ModuleIdentifier identifier, Boolean mustBeCleaned) {
        Module module = new Module(identifier, mustBeCleaned);

        resolvedDependencies.put(module.getIdentifier(), module);

        return module;
    }

    protected Boolean resourcesUpdated() {
        getLog().info("Verifying resources...");

        List<Resource> resources = project.getResources();

        return resourcesUpdated(project.getBuild().getOutputDirectory(), RESOURCES_LIST_FILE, resources);
    }

    protected Boolean testResourcesUpdated() {
        getLog().info("Verifying test resources...");

        List<Resource> resources = project.getTestResources();

        return resourcesUpdated(project.getBuild().getTestOutputDirectory(), TEST_RESOURCES_LIST_FILE, resources);
    }

    /**
     * Saving modification dates into a file because resources are put into with classes into the output dir
     */
    @SuppressWarnings("unchecked")
    protected Boolean resourcesUpdated(String outputDirectory, String resourceListFile, List<Resource> resources) {

        SetFileManager<String> previousResources = new SetFileManager<String>(getLog(), targetDirectory, resourceListFile);
        try {
            previousResources.load();
        } catch (IOException e) {
            getLog().error("Error loading previous resources file");
            return true;
        }

        SetFileManager<String> actualResources = new SetFileManager<String>(getLog(), targetDirectory, resourceListFile);

        boolean updateDetected = false;

        for (Resource resource : resources) {
            String source = resource.getDirectory();
            String target = StringUtils.isNotEmpty(resource.getTargetPath()) ? resource.getTargetPath() : outputDirectory;
            List<String> includes = resource.getIncludes();
            List<String> excludes = resource.getExcludes();

            getLog().debug("Resources excludes : " + excludes);
            getLog().debug("Resources includes : " + includes);

            if (!new File(source).exists()) {
                getLog().info("Resources directory does not exist " + source + ". Skipped...");
                continue;
            }

            DirectoryScanner scanner = new DirectoryScanner();

            scanner.setBasedir(source);

            if (includes != null && !includes.isEmpty()) {
                getLog().debug("add inclusion.");
                scanner.setIncludes(includes.toArray(new String[includes.size()]));
            }

            if (excludes != null && !excludes.isEmpty()) {
                getLog().debug("add exclusions.");
                scanner.setExcludes(excludes.toArray(new String[excludes.size()]));
            }
            scanner.addDefaultExcludes();

            getLog().debug("Starting resource scanning...");
            scanner.scan();

            String[] files = scanner.getIncludedFiles();
            getLog().debug(files.length + " resource files found");

            for (int i = 0; i < files.length; i++) {
                // extracting file path relative to resource dir
                String fileName = files[i];

                File sourceFile = new File(source + File.separator + fileName);
                File targetFile = new File(target + File.separator + fileName);

                Boolean isUpToDate = SelectorUtils.isOutOfDate(targetFile, sourceFile, 0);
                getLog().debug(
                        targetFile.getAbsolutePath() + " is uptodate : " + isUpToDate + " (compared to "
                                + sourceFile.getAbsolutePath() + ")");

                previousResources.remove(fileName);
                actualResources.add(fileName);

                if (!isUpToDate) {
                    getLog().info("resources updated, module have to be cleaned");
                    updateDetected = true;
                }
            }
        }
        // Not all previous file was found into the source directory
        // Assume some files was deleted into the source directory
        if (! previousResources.isEmpty()) {
            getLog().info("A resource was deleted, module have to be cleaned");
            updateDetected = true;
        }
        try {
            actualResources.save();
        } catch (IOException e) {
            getLog().warn("Error saving resource files list", e);
            updateDetected = true;
        }
        return updateDetected;
    }

    @SuppressWarnings("unchecked")
    private boolean parentUpdated() {
        getLog().info("Verifying parent modules...");

        Set<Artifact> artifacts = project.getArtifacts();

        for (Artifact artifact : artifacts) {
            String groupId = artifact.getGroupId();
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();

            ModuleIdentifier identifier = new ModuleIdentifier(groupId, artifactId, version);

            Module module = resolvedDependencies.get(identifier);

            if (getLog().isDebugEnabled()) {
                if (module != null) {
                    getLog().debug("Module " + identifier + " updated ? " + module.isUpdated());
                } else {
                    getLog().debug("Module " + identifier + " not found.");
                }
            }

            if (module != null && module.isUpdated()) {
                getLog().info("Module <" + groupId + ", " + artifactId + ", " + version + "> updated");
                return true;
            }
        }

        return false;
    }

    /**
     * Verify the pom was modified or not since last build.<br>
     *
     * @return
     */
    private boolean pomUpdated() {
        boolean modified = false;

        getLog().info("Verifying module descriptor ...");

        File file = project.getFile();
        String fileName = file.getAbsolutePath();

        Long currentModifiedTime = file.lastModified();
        Long lastModifiedTime = timestampManager.get(fileName);

        if (lastModifiedTime == null || currentModifiedTime.compareTo(lastModifiedTime) > 0) {
            getLog().info("Pom descriptor modification detected.");
            timestampManager.set(fileName, currentModifiedTime);
            modified = true;
        } else {
            getLog().debug("No modification on descriptor.");
        }

        return modified;
    }

    /**
     * for tests use only
     */
    protected void setProject(MavenProject project) {
        this.project = project;
    }

    /**
     * for tests use only
     */
    protected void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }
}

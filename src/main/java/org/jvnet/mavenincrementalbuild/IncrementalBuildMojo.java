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
package org.jvnet.mavenincrementalbuild;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.StringUtils;
import org.jvnet.mavenincrementalbuild.utils.TimestampsManager;

/**
 * Goal which touches a timestamp file.
 * 
 * @goal incremental-build
 * @phase validate
 * @requiresDependencyResolution test
 */
public class IncrementalBuildMojo extends AbstractMojo {

  /**
   * The Maven project.
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * Dependencies from the reactor. This attribute is a singleton for the complete build process
   */
  private final static Map<ModuleIdentifier, Module> resolvedDependencies = new HashMap<ModuleIdentifier, Module>();

  /**
   * the timestamp manager
   */
  private TimestampsManager timestampManager;

  /**
   * Set this to 'true' to deactivate the incremental build.
   * 
   * @parameter expression="${noIncrementalBuild}
   * @since 1.2
   */
  private boolean noIncrementalBuild;

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

    String targetDirectory = project.getBuild().getDirectory();

    if (getLog().isDebugEnabled()) {
      getLog().debug("Resolved modules : " + resolvedDependencies);
      getLog().debug("Loading previous timestamps ...");
    }

    try {
      timestampManager = new TimestampsManager(getLog(), targetDirectory);
      timestampManager.loadPreviousTimestamps();
    } catch (IOException e1) {
      getLog().error("Error loading previous timestamps", e1);
      throw new MojoExecutionException("Error loading previous timestamps.", e1);
    }

    module = saveModuleState(project, moduleIdentifier, pomUpdated() || parentUpdated() || resourcesUpdated()
        || sourcesUpdated());

    if (module.isUpdated()) {
      try {
        cleanModule();
      } catch (IOException e) {
        throw new MojoExecutionException("Unable to clean module.", e);
      }

    }

    getLog().debug("Saving timestamps..");
    try {
      timestampManager.saveTimestamps();
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
   * @param sourceDirectory base directory
   * @param targetDirectory the generated directory
   * @return true if a file in target directory is more recent than files in source directory, false otherwise
   */
  private Boolean directoryUpdated(File sourceDirectory, File targetDirectory) {
    getLog().debug("checking " + sourceDirectory + " compared to " + targetDirectory);

    Long lastSourceModificationDate = new Long(0);
    Long lastTargetModificationDate = new Long(0);

    if (!sourceDirectory.exists()) {
      getLog().info("No sources to check ...");
      return false;
    }

    if (!targetDirectory.exists()) {
      getLog().info("No target directory, build is required.");
      return true;
    }

    DirectoryScanner scanner = new DirectoryScanner();
    getLog().debug("Source directory : " + sourceDirectory);
    scanner.setBasedir(sourceDirectory);
    scanner.setIncludes(new String[]{"**/*"});
    scanner.setExcludes(DirectoryScanner.DEFAULTEXCLUDES);

    getLog().debug("Scanning sources...");
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
    }
    getLog().debug("Last source modification : " + lastSourceModificationDate);

    String targetDir = project.getBuild().getOutputDirectory();
    getLog().debug("Target directory : " + targetDir);

    scanner = new DirectoryScanner();
    scanner.setBasedir(project.getBuild().getOutputDirectory());
    scanner.setIncludes(new String[]{"**/*"});
    scanner.addDefaultExcludes();

    getLog().debug("Scanning output dir...");
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
      return true;
    } else {
      getLog().debug("No changes detected.");
      return false;
    }

  }

  /**
   * Check if modifications was done on the source folder since the last build
   * 
   * @return true if modification was detected, false otherwise
   */
  private Boolean sourcesUpdated() {
    getLog().info("Verifying sources...");
    File sourceDirectory = new File(project.getBuild().getSourceDirectory());
    File targetDirectory = new File(project.getBuild().getOutputDirectory());

    return directoryUpdated(sourceDirectory, targetDirectory);
  }

  private Module saveModuleState(MavenProject project, ModuleIdentifier identifier, Boolean mustBeCleaned) {
    Module module = new Module(identifier, mustBeCleaned);

    resolvedDependencies.put(module.getIdentifier(), module);

    return module;
  }

  @SuppressWarnings("unchecked")
  private Boolean resourcesUpdated() {
    getLog().info("Verifying resources...");
    List<Resource> resources = (List<Resource>) project.getResources();
    for (Resource resource : resources) {
      String source = resource.getDirectory();
      String target = StringUtils.isNotEmpty(resource.getTargetPath()) ? resource.getTargetPath() : project.getBuild()
          .getOutputDirectory();
      List<String> includes = (List<String>) resource.getIncludes();
      List<String> excludes = (List<String>) resource.getExcludes();

      getLog().debug("Resources excludes : " + excludes);
      getLog().debug("Resources includes : " + includes);

      if (!new File(source).exists()) {
        getLog().info("Resources directory does not exist : " + source);
        continue;
      }

      DirectoryScanner scanner = new DirectoryScanner();

      scanner.setBasedir(source);

      if (includes != null && !includes.isEmpty()) {
        getLog().debug("add inclusion.");
        scanner.setIncludes((String[]) includes.toArray(new String[includes.size()]));
      }

      if (excludes != null && !excludes.isEmpty()) {
        getLog().debug("add exclusions.");
        scanner.setExcludes((String[]) excludes.toArray(new String[excludes.size()]));
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
            targetFile.getAbsolutePath() + " is uptodate : " + isUpToDate + " (compare to "
                + sourceFile.getAbsolutePath() + ")");

        if (!isUpToDate) {
          getLog().info("resources updated, module have to be cleaned");
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean parentUpdated() {
    getLog().info("Verifying parent modules...");

    Set<Artifact> artifacts = (Set<Artifact>) project.getArtifacts();

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
    Long lastModifiedTime = timestampManager.getTimestamp(fileName);

    if (lastModifiedTime == null || currentModifiedTime.compareTo(lastModifiedTime) > 0) {
      getLog().info("Pom descriptor modification detected.");
      timestampManager.setTimestamp(fileName, currentModifiedTime);
      modified = true;
    } else {
      getLog().debug("No modification on descriptor.");
    }

    return modified;
  }
}

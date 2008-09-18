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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
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
	 * 
	 * @parameter default-value="${project.dependencies}"
	 * @required
	 * @readonly
	 */
	private Collection dependencies;

	/**
	 * The Maven project.
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * 
	 * @parameter default-value="${project.artifacts}"
	 * @required
	 * @readonly
	 */
	private Collection artifacts;

	/**
	 * Helper class to assist in attaching artifacts to the project instance.
	 * project-helper instance, used to make addition of resources simpler.
	 * 
	 * @component
	 * @required
	 * @readonly
	 */
	private MavenProjectHelper projectHelper;

	/**
	 * Dependencies from the reactor. This attribute is a singleton for the complete build process
	 */
	private final static Map<ModuleIdentifier, Module> resolvedDependencies = new HashMap<ModuleIdentifier, Module>();

	/**
	 * the timestamp manager
	 */
	private TimestampsManager timestampManager;

	public void execute() throws MojoExecutionException {
		Module module = null;

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
			throw new MojoExecutionException(
					"Error loading previous timestamps.", e1);
		}

		module = saveModuleState(project, pomUpdated() || resourcesUpdated()
				|| sourcesUpdated() || parentUpdated());

		if (module.isUpdated()) {
			getLog().debug("Module updated, cleaning target directory");

			try {
				FileUtils.deleteDirectory(targetDirectory);
				getLog().info(targetDirectory + " deleted");
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

	private Boolean sourcesUpdated() {
		getLog().info("Verifying sources...");
		Long lastSourceModificationDate = new Long(0);
		Long lastTargetModificationDate = new Long(0);
		File sourceDirectory = new File(project.getBuild().getSourceDirectory());
		File targetDirectory = new File(project.getBuild().getOutputDirectory());

		if ((!sourceDirectory.exists()) || (!targetDirectory.exists())) {
			getLog().info("No sources or target dir found...");
			return true;
		}

		DirectoryScanner scanner = new DirectoryScanner();
		getLog().debug("Source directory : " + sourceDirectory);
		scanner.setBasedir(sourceDirectory);
		scanner.setIncludes(new String[] { "**/*" });
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
		getLog().debug(
				"Last source modification : " + lastSourceModificationDate);

		String targetDir = project.getBuild().getOutputDirectory();
		getLog().debug("Target directory : " + targetDir);

		scanner = new DirectoryScanner();
		scanner.setBasedir(project.getBuild().getOutputDirectory());
		scanner.setIncludes(new String[] { "**/*" });
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
		getLog()
				.debug(
						"Last target modification date : "
								+ lastTargetModificationDate);

		if (lastSourceModificationDate > lastTargetModificationDate) {
			getLog().info("Source modification detected, clean will be called");
			return true;
		} else {
			getLog().debug("No changes detected.");
			return false;
		}

	}

	private Module saveModuleState(MavenProject project, Boolean mustBeCleaned) {
		ModuleIdentifier identifier = new ModuleIdentifier(
				project.getGroupId(), project.getArtifactId(), project
						.getVersion());
		Module module = new Module(identifier, mustBeCleaned);

		resolvedDependencies.put(identifier, module);

		return module;
	}

	@SuppressWarnings("unchecked")
	private Boolean resourcesUpdated() {
		getLog().info("Verifying resources...");
		List<Resource> resources = (List<Resource>) project.getResources();
		for (Resource resource : resources) {
			String source = resource.getDirectory();
			String target = StringUtils.isNotEmpty(resource.getTargetPath()) ? resource
					.getTargetPath()
					: project.getBuild().getOutputDirectory();
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
				scanner.setIncludes((String[]) includes
						.toArray(new String[includes.size()]));
			}

			if (excludes != null && !excludes.isEmpty()) {
				getLog().debug("add exclusions.");
				scanner.setExcludes((String[]) excludes
						.toArray(new String[excludes.size()]));
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

				Boolean isUpToDate = SelectorUtils.isOutOfDate(targetFile,
						sourceFile, 0);
				getLog().debug(
						targetFile.getAbsolutePath() + " is uptodate : "
								+ isUpToDate + " (compare to "
								+ sourceFile.getAbsolutePath() + ")");

				if (!isUpToDate) {
					getLog().info(
							"resources updated, module have to be cleaned");
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private boolean parentUpdated() {
		getLog().info("Verifying parent modules...");

		List<Dependency> dependencies = (List<Dependency>) project
				.getDependencies();

		for (Dependency dependency : dependencies) {
			String groupId = dependency.getGroupId();
			String artifactId = dependency.getArtifactId();
			String version = dependency.getVersion();

			ModuleIdentifier identifier = new ModuleIdentifier(groupId,
					artifactId, version);

			Module module = resolvedDependencies.get(identifier);

			if (getLog().isDebugEnabled()) {
				if (module != null) {
					getLog().debug(
							"Module " + identifier + " updated ? "
									+ module.isUpdated());
				} else {
					getLog().debug("Module " + identifier + " not found.");
				}
			}

			if (module != null && module.isUpdated()) {
				getLog().info(
						"Module <" + groupId + ", " + artifactId + ", "
								+ version + "> updated");
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

		if (lastModifiedTime == null
				|| currentModifiedTime.compareTo(lastModifiedTime) > 0) {
			getLog().info("Pom descriptor modification detected.");
			timestampManager.setTimestamp(fileName, currentModifiedTime);
			modified = true;
		} else {
			getLog().debug("No modification on descriptor.");
		}

		return modified;
	}
}

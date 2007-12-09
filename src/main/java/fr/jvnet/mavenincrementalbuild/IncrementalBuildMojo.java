package fr.jvnet.mavenincrementalbuild;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
	 * Dependencies from the reactor
	 */
	private final static Map<ModuleIdentifier, Module> resolvedDependencies = new HashMap<ModuleIdentifier, Module>();

	public void execute() throws MojoExecutionException {
		Module module = null;
		String buildDirectory = project.getBuild().getOutputDirectory();

		if (getLog().isDebugEnabled()) {
			getLog().debug("Resolved modules : " + resolvedDependencies);
		}

		boolean resourcesUpdated = resourcesUpdated();
		boolean sourcesUpdated = sourcesUpdated();
		boolean parentUpdated = parentUpdated();

		module = saveModuleState(project, resourcesUpdated || sourcesUpdated
				|| parentUpdated);
		
		if (module.isUpdated()) {
			getLog().debug("Module updated, cleaning target directory");

			try {
				FileUtils.deleteDirectory(buildDirectory);
				getLog().info(buildDirectory + " deleted");
			} catch(IOException e) {
				throw new MojoExecutionException("Unable to clean module.", e);
			}
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

			if (getLog().isDebugEnabled() && module != null) {
				getLog().debug(
						"Module " + identifier + " updated ? "
								+ module.isUpdated());
			} else {
				getLog().debug("Module " + identifier + " not found.");
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
}

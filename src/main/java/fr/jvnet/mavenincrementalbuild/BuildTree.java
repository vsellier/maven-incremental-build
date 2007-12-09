package fr.jvnet.mavenincrementalbuild;

import java.util.HashMap;
import java.util.Map;

public class BuildTree {
	private final static BuildTree instance = new BuildTree();

	private Map<ModuleIdentifier, Module> modules = new HashMap<ModuleIdentifier, Module>();

	public static BuildTree getInstance() {
		return instance;
	}

	public Module getModule(String groupId, String artifactId, String version) {
		ModuleIdentifier identifier = new ModuleIdentifier(groupId, artifactId,
				version);
		return getModule(identifier);
	}

	public Module getModule(ModuleIdentifier identifier) {
		return modules.get(identifier);
	}

	public void addModule(Module module) {
		modules.put(module.getIdentifier(), module);
	}
	
}

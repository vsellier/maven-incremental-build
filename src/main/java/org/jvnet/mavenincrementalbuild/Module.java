package org.jvnet.mavenincrementalbuild;

public class Module {
	private ModuleIdentifier identifier;
	private Boolean updated;

	public Module(ModuleIdentifier identifier, Boolean updated) {
		super();
		this.identifier = identifier;
		this.updated = updated;
	}

	public ModuleIdentifier getIdentifier() {
		return identifier;
	}

	public Boolean isUpdated() {
		return updated;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((identifier == null) ? 0 : identifier.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Module other = (Module) obj;
		if (identifier == null) {
			if (other.identifier != null)
				return false;
		} else if (!identifier.equals(other.identifier))
			return false;
		return true;
	}

}

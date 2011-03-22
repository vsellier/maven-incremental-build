package net.java.mavenincrementalbuild.utils;

import org.apache.maven.plugin.logging.Log;

import java.util.Set;
import java.util.TreeSet;

public class SetFileManager<T> extends FilePersistence<Set<T>> {

    /**
     * Persist a list of string in a file
     *
     * @param logger    the current maven logger
     * @param directory the directory
     * @param name      the file name
     */
    public SetFileManager(Log logger, String directory, String name) {
        super(logger, directory, name);
        data = new TreeSet<T>();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public boolean contains(T value) {
        return data.contains(value);
    }

    public void add(T value) {
        data.add(value);
    }

    public boolean remove(T value) {
        return data.remove(value);
    }

}

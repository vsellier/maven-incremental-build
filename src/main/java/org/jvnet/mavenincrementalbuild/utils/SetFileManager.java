package org.jvnet.mavenincrementalbuild.utils;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetFileManager<T> extends FilePersistence<Set<T>> {

    /**
     * Persist a list of string in a file
     * @param logger the current maven logger
     * @param directory the directory
     * @param name the file name
     */
    public SetFileManager(Log logger, String directory, String name) {
        super(logger, directory, name);
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

package org.jvnet.mavenincrementalbuild.utils;

import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Serialize an object to a file
 */
public class FilePersistence<T> {
    private final Log LOGGER;

    private File dataFile;
    private String directory;
    private String path;

    protected T data;

    public FilePersistence(Log LOGGER, String directory, String fileName) {
        this.LOGGER = LOGGER;
        this.directory = directory;

        this.path = directory + File.separator + fileName;

        dataFile = new File(path);
        LOGGER.debug("Using file : " + dataFile.getAbsolutePath());
    }

    public void load() throws IOException {
        if (dataFile.exists()) {
            LOGGER.debug("Loading previous data in " + path + " ...");
            ObjectInputStream input = new ObjectInputStream(
                    new FileInputStream(dataFile));
            try {
                data = (T) input.readObject();
            } catch (ClassNotFoundException e) {
                LOGGER.error("Error deserializing file : ", e);
                throw new RuntimeException(
                        "Error deserializing file.", e);
            }
            input.close();
        } else {
            LOGGER.debug("Previous file " + path + " not found.");
        }
    }

    public void save() throws IOException {
        LOGGER.debug("Saving file " + path + " ...");

        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        ObjectOutputStream output = new ObjectOutputStream(
                new FileOutputStream(dataFile));
        output.writeObject(data);
        output.flush();
        output.close();
    }

}

package org.jvnet.mavenincrementalbuild.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;

/**
 * Managed timestamps file.
 * 
 * @author Vincent Sellier
 * 
 */
public class TimestampsManager {
	private final Log LOGGER;
	private final static String FILE_NAME = "timestamp";

	private File timestampsFile;
	private String directory;

	private Map<String, Long> timestamps;

	/**
	 * Create the TimestampFile manager.
	 * 
	 * @param directory
	 *            the dir where the timestamps file will be stored
	 * @throws IOException
	 */
	public TimestampsManager(Log logger, String directory) throws IOException {
		this.LOGGER = logger;

		this.directory = directory;

		timestampsFile = new File(directory + File.separator + FILE_NAME);
		LOGGER.debug("Timestamps file : " + timestampsFile.getAbsolutePath());
	}

	public void loadPreviousTimestamps() throws IOException {

		if (timestampsFile.exists()) {
			LOGGER.debug("Loading previous timestamps ...");
			ObjectInputStream input = new ObjectInputStream(
					new FileInputStream(timestampsFile));
			try {
				timestamps = (Map<String, Long>) input.readObject();
			} catch (ClassNotFoundException e) {
				LOGGER.error("Error deserializing timestamp file : ", e);
				throw new RuntimeException(
						"Error deserializing timestamp file.", e);
			}
			input.close();
		} else {
			LOGGER.debug("Previous timestamps file not found.");
			timestamps = new HashMap<String, Long>();
		}
	}

	public void saveTimestamps() throws IOException {
		LOGGER.debug("Saving timestamps file...");

		File dir = new File(directory);
		if (!dir.exists()) {
			dir.mkdirs();
		}

		ObjectOutputStream output = new ObjectOutputStream(
				new FileOutputStream(timestampsFile));
		output.writeObject(timestamps);
		output.flush();
		output.close();
	}

	public Long getTimestamp(String fileName) {
		return timestamps.get(fileName);
	}

	public void setTimestamp(String fileName, Long timestamp) {
		timestamps.put(fileName, timestamp);
	}
}

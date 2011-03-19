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

/**
 * Managed timestamps file.
 * 
 * @author Vincent Sellier
 * 
 */
public class MapFileManager<K, V> extends FilePersistence<Map<K, V>> {

	/**
	 * Create the MapFile manager.
	 * 
	 * @param directory
	 *            the dir where the timestamps file will be stored
	 * @throws IOException
	 */
	public MapFileManager(Log logger, String directory, String fileName) throws IOException {
        super(logger, directory, fileName);
        data = new HashMap<K, V>();
	}


	public V get(K key) {
		return data.get(key);
	}

	public void set(K key, V value) {
		data.put(key, value);
	}
}

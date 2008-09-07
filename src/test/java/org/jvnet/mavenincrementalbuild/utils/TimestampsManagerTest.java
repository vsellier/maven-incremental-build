package org.jvnet.mavenincrementalbuild.utils;

import java.io.IOException;

import junit.framework.*;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

public class TimestampsManagerTest extends TestCase {
	private final static String OUTPUT_DIRECTORY = "target";

	private Log logger;
	
	public void setUp() {
		logger = new SystemStreamLog();
	}
	
	public void testSerializationAndDeserialization() throws Exception {
		TimestampsManager manager = new TimestampsManager(logger, OUTPUT_DIRECTORY);
		
		manager.loadPreviousTimestamps();
		manager.setTimestamp("test", new Long(10));
		manager.setTimestamp("test2", new Long(100));
		
		manager.saveTimestamps();
		
		manager = new TimestampsManager(logger, OUTPUT_DIRECTORY);
		manager.loadPreviousTimestamps();
		assertEquals("Wrong timestamp", new Long(10), manager.getTimestamp("test"));
		assertEquals("Wrong timestamp", new Long(100), manager.getTimestamp("test2"));		
	}
}

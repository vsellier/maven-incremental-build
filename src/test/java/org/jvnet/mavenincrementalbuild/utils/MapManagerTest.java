package org.jvnet.mavenincrementalbuild.utils;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class MapManagerTest {
    private final static String OUTPUT_DIRECTORY = "target";

    private Log logger;

    @Before
    public void setUp() {
        logger = new SystemStreamLog();
    }

    @Test
    public void testSerializationAndDeserialization() throws Exception {
        MapFileManager<String, Long> manager = new MapFileManager(logger,
                OUTPUT_DIRECTORY, "timestamp");

        manager.load();
        manager.set("test", new Long(10));
        manager.set("test2", new Long(100));

        manager.save();

        manager = new MapFileManager(logger, OUTPUT_DIRECTORY, "timestamp");
        manager.load();
        assertEquals("Wrong timestamp", new Long(10), manager.get("test"));
        assertEquals("Wrong timestamp", new Long(100), manager.get("test2"));
    }
}

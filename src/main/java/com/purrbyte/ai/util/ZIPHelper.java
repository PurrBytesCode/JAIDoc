package com.purrbyte.ai.util;

import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZIPHelper {

    /**
     * Finds a ZIP entry by filename, searching recursively (entries may be under a version-prefixed directory).
     */
    public static ZipEntry findZipEntry(ZipFile zipFile, String name) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.equals(name)
                    || entryName.endsWith("/" + name)
                    || entryName.replace('\\', '/').endsWith("/" + name)) {
                return entry;
            }
        }
        return null;
    }
}

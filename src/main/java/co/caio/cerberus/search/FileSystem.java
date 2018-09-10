package co.caio.cerberus.search;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystem {
    private static Logger logger = LoggerFactory.getLogger(FileSystem.class);

    static Directory openDirectory(Path dir) throws Exception {
        if (! dir.toFile().exists() && dir.getParent().toFile().isDirectory()) {
            logger.debug("Creating directory %s", dir);
            Files.createDirectory(dir);
        }
        return FSDirectory.open(dir);
    }
}

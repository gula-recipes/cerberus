package co.caio.cerberus.search;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

class IndexConfiguration {
  private static final String INDEX_DIR_NAME = "index";
  private static final String TAXONOMY_DIR_NAME = "taxonomy";

  static final String CONFIG_NAME = "config.properties";
  private static final String CONFIG_MULTI_VALUED_KEY = "multiValued";

  private final FacetsConfig facetsConfig;
  private final Analyzer analyzer;

  private final Path baseDirectory;

  IndexConfiguration(Path baseDirectory, Set<String> multiValuedDimensions) {
    this.baseDirectory = baseDirectory;
    this.analyzer = new EnglishAnalyzer();

    this.facetsConfig = new FacetsConfig();
    multiValuedDimensions.forEach(c -> facetsConfig.setMultiValued(c, true));
  }

  void save() throws IOException {
    var props = new Properties();
    props.setProperty(
        CONFIG_MULTI_VALUED_KEY,
        facetsConfig
            .getDimConfigs()
            .entrySet()
            .stream()
            .filter(e -> e.getValue().multiValued)
            .map(Entry::getKey)
            .collect(Collectors.joining(",")));

    props.store(new FileWriter(baseDirectory.resolve(CONFIG_NAME).toFile()), null);
  }

  FacetsConfig getFacetsConfig() {
    return facetsConfig;
  }

  Analyzer getAnalyzer() {
    return analyzer;
  }

  Directory openIndexDirectory() throws IOException {
    return FSDirectory.open(baseDirectory.resolve(INDEX_DIR_NAME));
  }

  Directory openTaxonomyDirectory() throws IOException {
    return FSDirectory.open(baseDirectory.resolve(TAXONOMY_DIR_NAME));
  }

  static IndexConfiguration fromBaseDirectory(Path baseDirectory) throws IOException {
    var configPath = baseDirectory.resolve(CONFIG_NAME);
    var props = new Properties();

    props.load(new FileReader(configPath.toFile()));

    var csv = props.getProperty(CONFIG_MULTI_VALUED_KEY);

    if (csv == null) {
      throw new IOException("Invalid configuration file");
    }

    var multiValuedDimensions = Arrays.stream(csv.split(",")).collect(Collectors.toSet());

    return new IndexConfiguration(baseDirectory, multiValuedDimensions);
  }
}

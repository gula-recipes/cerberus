package co.caio.cerberus.search;

import static co.caio.cerberus.search.IndexField.*;

import org.apache.lucene.facet.FacetsConfig;

public class FacetConfiguration {
  private static FacetsConfig facetsConfig = null;

  public static FacetsConfig getFacetsConfig() {
    if (facetsConfig == null) {
      facetsConfig = new FacetsConfig();
      facetsConfig.setIndexFieldName(FACET_DIET, FACET_DIET);
      facetsConfig.setMultiValued(FACET_DIET, true);

      facetsConfig.setIndexFieldName(FACET_KEYWORD, FACET_KEYWORD);
      facetsConfig.setMultiValued(FACET_KEYWORD, true);
    }
    return facetsConfig;
  }
}

package co.caio.cerberus.search;

import static co.caio.cerberus.search.IndexField.*;

import co.caio.cerberus.model.FacetData;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TopDocs;

class SearcherImpl implements Searcher {

  private static final Sort sortNumIngredients = integerSorterWithDefault(NUM_INGREDIENTS);
  private static final Sort sortPrepTime = integerSorterWithDefault(PREP_TIME);
  private static final Sort sortCookTime = integerSorterWithDefault(COOK_TIME);
  private static final Sort sortTotalTime = integerSorterWithDefault(TOTAL_TIME);
  private static final Sort sortCalories = integerSorterWithDefault(CALORIES);

  private final IndexSearcher indexSearcher;
  private final TaxonomyReader taxonomyReader;
  private final IndexConfiguration indexConfiguration;
  private final FulltextQueryParser queryParser;
  private final MoreLikeThis moreLikeThis;

  SearcherImpl(Path dir) throws IOException {
    indexConfiguration = IndexConfiguration.fromBaseDirectory(dir);

    var indexReader = DirectoryReader.open(indexConfiguration.openIndexDirectory());

    this.indexSearcher = new IndexSearcher(indexReader);
    this.taxonomyReader = new DirectoryTaxonomyReader(indexConfiguration.openTaxonomyDirectory());

    queryParser = new FulltextQueryParser(indexConfiguration.getAnalyzer());
    moreLikeThis = new MoreLikeThis(indexReader);
    moreLikeThis.setAnalyzer(indexConfiguration.getAnalyzer());
  }

  private static Sort integerSorterWithDefault(String fieldName) {
    var field = new SortField(fieldName, Type.INT);
    field.setMissingValue(Integer.MAX_VALUE);
    return new Sort(field, SortField.FIELD_SCORE);
  }

  public SearchResult search(SearchQuery query) {
    try {
      return _search(query);
    } catch (IOException wrapped) {
      throw new SearcherException(wrapped);
    }
  }

  @Override
  public SearchResult findSimilar(String recipeText, int maxResults) {
    try {
      var query = parseSimilarity(recipeText);
      var result = indexSearcher.search(query, maxResults);

      var builder = new SearchResult.Builder().totalHits(result.totalHits.value);

      for (int i = 0; i < result.scoreDocs.length; i++) {
        Document doc = indexSearcher.doc(result.scoreDocs[i].doc);
        builder.addRecipe(doc.getField(RECIPE_ID).numericValue().longValue());
      }

      return builder.build();
    } catch (IOException wrapped) {
      throw new SearcherException(wrapped);
    }
  }

  Query parseSimilarity(String recipeText) {
    try {
      return moreLikeThis.like(FULL_RECIPE, new StringReader(recipeText));
    } catch (IOException wrapped) {
      throw new SearcherException(wrapped);
    }
  }

  public int numDocs() {
    return indexSearcher.getIndexReader().numDocs();
  }

  private SearchResult _search(SearchQuery query) throws IOException {
    final int maxFacets = query.maxFacets();

    var luceneQuery = indexSearcher.rewrite(toLuceneQuery(query));

    final int count = indexSearcher.count(luceneQuery);
    final boolean computeFacets = maxFacets > 0 && canComputeFacets(count);

    var builder = new SearchResult.Builder().totalHits(count);
    TopDocs result;

    if (computeFacets) {
      var fc = new FacetsCollector();

      result =
          FacetsCollector.search(
              indexSearcher,
              luceneQuery,
              query.offset() + query.maxResults(),
              toLuceneSort(query.sort()),
              fc);

      var staticFacets =
          new FastTaxonomyFacetCounts(taxonomyReader, indexConfiguration.getFacetsConfig(), fc);

      staticFacets.getAllDims(maxFacets).forEach(fr -> addFacetData(builder, fr));

    } else {
      result =
          indexSearcher.search(
              luceneQuery, query.offset() + query.maxResults(), toLuceneSort(query.sort()));
    }

    for (int i = query.offset(); i < result.scoreDocs.length; i++) {
      Document doc = indexSearcher.doc(result.scoreDocs[i].doc);
      builder.addRecipe(doc.getField(RECIPE_ID).numericValue().longValue());
    }

    return builder.build();
  }

  boolean canComputeFacets(int unused) {
    return true;
  }

  private void addFacetData(SearchResult.Builder sb, FacetResult fr) {
    if (fr == null) {
      return;
    }

    var facetDataBuilder = new FacetData.Builder().dimension(fr.dim);
    for (int i = 0; i < fr.labelValues.length; i++) {
      facetDataBuilder.putChildren(fr.labelValues[i].label, fr.labelValues[i].value.longValue());
    }
    sb.putFacets(fr.dim, facetDataBuilder.build());
  }

  Query parseFulltext(String fulltext) {
    return queryParser.parse(fulltext);
  }

  Sort toLuceneSort(SortOrder sortOrder) {
    switch (sortOrder) {
      case RELEVANCE:
        return Sort.RELEVANCE;
      case NUM_INGREDIENTS:
        return sortNumIngredients;
      case PREP_TIME:
        return sortPrepTime;
      case COOK_TIME:
        return sortCookTime;
      case TOTAL_TIME:
        return sortTotalTime;
      case CALORIES:
        return sortCalories;
      default:
        throw new IllegalStateException(String.format("Unhandled sort order: %s", sortOrder));
    }
  }

  Query toLuceneQuery(SearchQuery searchQuery) {
    var queryBuilder = new BooleanQuery.Builder();

    searchQuery
        .fulltext()
        .ifPresent(fulltext -> queryBuilder.add(parseFulltext(fulltext), Occur.MUST));

    searchQuery
        .numIngredients()
        .ifPresent(
            range ->
                queryBuilder.add(
                    IntPoint.newRangeQuery(NUM_INGREDIENTS, range.start(), range.end()),
                    Occur.MUST));

    searchQuery
        .cookTime()
        .ifPresent(
            range ->
                queryBuilder.add(
                    IntPoint.newRangeQuery(COOK_TIME, range.start(), range.end()), Occur.MUST));

    searchQuery
        .prepTime()
        .ifPresent(
            range ->
                queryBuilder.add(
                    IntPoint.newRangeQuery(PREP_TIME, range.start(), range.end()), Occur.MUST));

    searchQuery
        .totalTime()
        .ifPresent(
            range ->
                queryBuilder.add(
                    IntPoint.newRangeQuery(TOTAL_TIME, range.start(), range.end()), Occur.MUST));

    searchQuery
        .calories()
        .ifPresent(
            range ->
                queryBuilder.add(
                    IntPoint.newRangeQuery(CALORIES, range.start(), range.end()), Occur.MUST));

    searchQuery
        .fatContent()
        .ifPresent(
            range ->
                queryBuilder.add(
                    FloatPoint.newRangeQuery(FAT_CONTENT, range.start(), range.end()), Occur.MUST));

    searchQuery
        .proteinContent()
        .ifPresent(
            range ->
                queryBuilder.add(
                    FloatPoint.newRangeQuery(PROTEIN_CONTENT, range.start(), range.end()),
                    Occur.MUST));

    searchQuery
        .carbohydrateContent()
        .ifPresent(
            range ->
                queryBuilder.add(
                    FloatPoint.newRangeQuery(CARBOHYDRATE_CONTENT, range.start(), range.end()),
                    Occur.MUST));

    searchQuery
        .diet()
        .ifPresent(
            (diet) ->
                queryBuilder.add(
                    FloatPoint.newRangeQuery(
                        getFieldNameForDiet(diet.name()), diet.threshold(), Float.MAX_VALUE),
                    Occur.MUST));

    return queryBuilder.build();
  }
}

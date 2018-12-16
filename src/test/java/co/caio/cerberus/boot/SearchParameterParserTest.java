package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class SearchParameterParserTest {

  private final SearchParameterParser parser = new SearchParameterParser();

  @Test
  void buildQuery() {
    var input = new HashMap<String, String>();
    var builder = new SearchQuery.Builder();

    input.put("q", "oil");
    assertEquals(parser.buildQuery(input), builder.fulltext("oil").build());

    input.put("sort", "cook_time");
    assertEquals(parser.buildQuery(input), builder.sort(SortOrder.COOK_TIME).build());

    input.put("n", "42");
    assertEquals(parser.buildQuery(input), builder.maxResults(42).build());

    input.put("ni", "5,10");
    assertEquals(parser.buildQuery(input), builder.numIngredients(RangedSpec.of(5, 10)).build());

    input.put("nf", "12");
    assertEquals(parser.buildQuery(input), builder.maxFacets(12).build());
  }

  @Test
  void unknownParameterThrows() {
    assertThrows(
        SearchParameterException.class,
        () -> parser.buildQuery(Collections.singletonMap("unknown", "doesn't matter")));
  }

  @Test
  void parseSortOrder() {

    for (SortOrder order : SortOrder.values()) {
      var parsed = parser.parseSortOrder(order.name().toLowerCase());
      assertEquals(order, parsed);
    }

    // Any other value should throw
    assertThrows(SearchParameterException.class, () -> parser.parseSortOrder("invalid sort"));
  }

  @Test
  void parseRange() {
    // Plain numbers are treated as [0,number]
    assertEquals(RangedSpec.of(0, 10), parser.parseRange("10"));
    // Ranges are encoded as "numberA,numberB"
    assertEquals(RangedSpec.of(1, 10), parser.parseRange("1,10"));

    assertThrows(SearchParameterException.class, () -> parser.parseRange("asd"));

    assertThrows(SearchParameterException.class, () -> parser.parseRange(",10"));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("10,"));

    assertThrows(SearchParameterException.class, () -> parser.parseRange("1,notANumber"));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("1,10hue"));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("10,10 "));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("  10,10"));

    // Verify that we throw when there's still stuff after the range spec
    assertThrows(SearchParameterException.class, () -> parser.parseRange("10,10,10"));
    // And that inverted ranges are handled as errors
    assertThrows(SearchParameterException.class, () -> parser.parseRange("5,1"));
  }
}
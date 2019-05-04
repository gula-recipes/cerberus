package co.caio.cerberus.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.immutables.value.Value;

@ImmutableStyle
@JsonSerialize(as = ImmutableSearchQuery.class)
@JsonDeserialize(as = ImmutableSearchQuery.class)
@Value.Immutable
public interface SearchQuery {
  Optional<String> fulltext();

  Optional<RangedSpec> numIngredients();

  Optional<RangedSpec> prepTime();

  Optional<RangedSpec> cookTime();

  Optional<RangedSpec> totalTime();

  Optional<RangedSpec> calories();

  Optional<RangedSpec> fatContent();

  Optional<RangedSpec> proteinContent();

  Optional<RangedSpec> carbohydrateContent();

  Map<String, Float> dietThreshold();

  @Value.Derived
  default long numSelectedFilters() {
    return Stream.of(
                numIngredients(),
                prepTime(),
                cookTime(),
                totalTime(),
                calories(),
                fatContent(),
                proteinContent(),
                carbohydrateContent())
            .flatMap(Optional::stream)
            .count()
        + dietThreshold().size();
  }

  @Value.Derived
  default boolean isFulltextOnly() {
    return fulltext().isPresent() && numSelectedFilters() == 0;
  }

  @Value.Derived
  default boolean isEmpty() {
    return numSelectedFilters() == 0 && fulltext().isEmpty();
  }

  @Value.Default
  default int maxResults() {
    return 10;
  }

  @Value.Default
  default int maxFacets() {
    return 0;
  }

  @Value.Default
  default int offset() {
    return 0;
  }

  enum SortOrder {
    RELEVANCE,
    NUM_INGREDIENTS,
    PREP_TIME,
    COOK_TIME,
    TOTAL_TIME,
    CALORIES
  }

  @Value.Default
  default SortOrder sort() {
    return SortOrder.RELEVANCE;
  }

  @Value.Immutable(builder = false)
  @JsonFormat(shape = JsonFormat.Shape.ARRAY)
  @JsonPropertyOrder({"start", "end"})
  @JsonSerialize(as = ImmutableRangedSpec.class)
  @JsonDeserialize(as = ImmutableRangedSpec.class)
  interface RangedSpec {
    @Value.Parameter
    int start();

    @Value.Parameter
    int end();

    static RangedSpec of(int start, int end) {
      return ImmutableRangedSpec.of(start, end);
    }

    @Value.Check
    default void check() {
      if (start() > end()) {
        throw new IllegalStateException("Range start must be before range end");
      }
      if (start() < 0 || end() < 0) {
        throw new IllegalStateException("Range must not contain negative numbers");
      }
    }
  }

  @Value.Check
  default void check() {
    if (maxResults() < 1) {
      throw new IllegalStateException("maxResults must be >= 1");
    }
    if (maxFacets() < 0) {
      throw new IllegalStateException("maxFacets must be >= 0");
    }
    if (offset() < 0) {
      throw new IllegalStateException("offset must be >= 0");
    }
    dietThreshold()
        .forEach(
            (diet, score) -> {
              if (score <= 0 || score > 1) {
                throw new IllegalStateException("score must be in ]0,1]");
              }
            });
  }

  class Builder extends ImmutableSearchQuery.Builder {
    public Builder addMatchDiet(String dietName) {
      putDietThreshold(dietName, 1f);
      return this;
    }
  }
}

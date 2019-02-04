package co.caio.cerberus.db;

import co.caio.cerberus.flatbuffers.FlatRecipe;
import co.caio.cerberus.model.Recipe;
import java.util.List;
import java.util.OptionalInt;

public interface RecipeMetadata {
  long getRecipeId();

  String getName();

  String getSlug();

  String getCrawlUrl();

  String getSiteName();

  List<String> getInstructions();

  List<String> getIngredients();

  int getNumIngredients();

  OptionalInt getTotalTime();

  OptionalInt getCalories();

  static RecipeMetadata fromRecipe(Recipe recipe) {
    return new RecipeMetadataRecipeAdapter(recipe);
  }

  static RecipeMetadata fromFlatRecipe(FlatRecipe recipe) {
    return new RecipeMetadataFlatRecipeCopy(recipe);
  }

  static RecipeMetadata fromFlatRecipeAsProxy(FlatRecipe recipe) {
    return new RecipeMetadataFlatRecipeAdapter(recipe);
  }
}
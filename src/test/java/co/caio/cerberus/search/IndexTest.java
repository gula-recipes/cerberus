package co.caio.cerberus.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexNotFoundException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {
    @Test
    public void badUsage() {
        var exc = Index.Builder.IndexBuilderException.class;
        var builder = new Index.Builder();
        assertAll("index builder",
                () -> assertThrows(exc, builder::build),
                () -> assertThrows(exc, () -> builder.reset().createMode().build()),
                () -> assertThrows(exc, () -> builder.reset().analyzer(new StandardAnalyzer()).build()),
                () -> assertThrows(exc, () -> builder.reset().directory(Paths.get("void")).createMode().build())
        );
        // This would be valid if it weren't for the mode being append
        assertThrows(IndexNotFoundException.class, () -> builder.reset().inMemory().appendMode().build());
    }

    @Test
    public void simpleLocalIndexer() throws IOException {
        var tempDir = Files.createTempDirectory("cerberus-test");
        var index = new Index.Builder().directory(tempDir).createOrAppendMode().build();
        assertEquals(0, index.numDocs());
        index.addRecipe(RecipeTest.basicBuild());
        assertEquals(1, index.numDocs());
        index.close();

        // Reopening it should still allow us to read its documents
        var newIndexSameDir = new Index.Builder().directory(tempDir).appendMode().build();
        assertEquals(1, newIndexSameDir.numDocs());
        newIndexSameDir.close();

        // But opening should erase the old data
        var destructiveIndex = new Index.Builder().directory(tempDir).createMode().build();
        assertEquals(0, destructiveIndex.numDocs());
    }
}
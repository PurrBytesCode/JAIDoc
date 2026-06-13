package com.purrbyte.ai.doclet;

import com.purrbyte.ai.test.BaseTest;
import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag(BaseTest.TAG_UNIT)
class ChunkWriterTest extends UnitTest {

    @TempDir
    Path tempDir;

   /**
     * ChunkWriter constructor enforces maxChars >= 500.
     * Use values > 500 to test splitting behavior.
     */
    private ChunkWriter createChunkWriter(int maxChars) throws IOException {
        Path file = tempDir.resolve("chunks.jsonl");
        return new ChunkWriter(file, jsonMapper, maxChars, 10, false);
    }

    /**
     * ChunkWriter constructor for testing the documented-only mode.
     */
    private ChunkWriter createDocumentedOnlyChunkWriter(int maxChars) throws IOException {
        Path file = tempDir.resolve("chunks.jsonl");
        return new ChunkWriter(file, jsonMapper, maxChars, 10, true);
    }

    @Test
    @Order(1)
    void split_shortText_noSplit() throws IOException {
        try (ChunkWriter writer = createChunkWriter(600)) {
            String text = "This is a short text that fits within the limit";
            List<String> parts = writer.split(text);
            assertThat(parts).hasSize(1);
            assertThat(parts.getFirst()).isEqualTo(text);
        }
    }

    @Test
    @Order(2)
    void split_longTextWithParagraphs_splitsOnParagraph() throws IOException {
        // Build text programmatically to ensure it's > 800 chars
        String paragraph = "This is a paragraph with many words and information for testing the text splitting logic. ";
        String text = paragraph.repeat(10) + "\n\n" + paragraph.repeat(10);
        assertThat(text.length()).as("Text should be over 800 chars").isGreaterThan(800);
        try (ChunkWriter writer = createChunkWriter(800)) {
            List<String> parts = writer.split(text);
            assertThat(parts).hasSizeGreaterThan(1);
            assertThat(parts.getFirst()).contains("This is a paragraph");
        }
    }

    @Test
    @Order(3)
    void split_longTextWithLines_splitsOnLine() throws IOException {
        String line = "This is a line with many words and information for testing the line splitting logic. ";
        String text = line.repeat(10) + "\n" + line.repeat(10) + "\n" + line.repeat(10);
        assertThat(text.length()).as("Text should be over 800 chars").isGreaterThan(800);
        try (ChunkWriter writer = createChunkWriter(800)) {
            List<String> parts = writer.split(text);
            assertThat(parts).hasSizeGreaterThan(1);
        }
    }

    @Test
    @Order(4)
    void split_longTextWithoutSpaces_splitsOnWordBoundary() throws IOException {
        String text = "Hello world this is a long text without many spaces so we can test the word boundary splitting logic";
        try (ChunkWriter writer = createChunkWriter(600)) {
            List<String> parts = writer.split(text);
            assertThat(parts).hasSize(1);
        }
    }

    @Test
    @Order(5)
    void documentedOnly_shortText_skipped() throws IOException {
        try (ChunkWriter writer = createDocumentedOnlyChunkWriter(600)) {
            String text = "This is a short text that fits within the limit";
            ObjectNode metadata = jsonMapper.createObjectNode();
            writer.write("id-1", text, metadata, false);
            assertThat(writer.count()).isZero();
        }
    }

    @Test
    @Order(6)
    void documentedOnly_shortText_written() throws IOException {
        try (ChunkWriter writer = createDocumentedOnlyChunkWriter(600)) {
            String text = "This is a short text that fits within the limit";
            ObjectNode metadata = jsonMapper.createObjectNode();
            writer.write("id-1", text, metadata, true);
            assertThat(writer.count()).isEqualTo(1);
        }
    }

    @Test
    @Order(7)
    void documentedOnly_longText_splitsAndCounts() throws IOException {
        String paragraph = "This is a paragraph with many words and information for testing the text splitting logic. ";
        String text = paragraph.repeat(10) + "\n\n" + paragraph.repeat(10);
        assertThat(text.length()).as("Text should be over 800 chars").isGreaterThan(800);
        try (ChunkWriter writer = createDocumentedOnlyChunkWriter(800)) {
            ObjectNode metadata = jsonMapper.createObjectNode();
            writer.write("id-1", text, metadata, true);
            assertThat(writer.count()).isGreaterThan(1);
        }
    }

    @Test
    @Order(8)
    void bestBreak_paragraphBoundary_returnsParagraphPosition() throws IOException {
        String paragraph = "This is a paragraph with many words and information for testing the text splitting logic. ";
        String text = paragraph.repeat(10) + "\n\n" + paragraph.repeat(10);
        assertThat(text.length()).as("Text should be over 800 chars").isGreaterThan(800);
        try (ChunkWriter writer = createChunkWriter(800)) {
            int breakPos = writer.bestBreak(text, 0, 800);
            assertThat(breakPos).isGreaterThan(0);
        }
    }

    @Test
    @Order(9)
    void bestBreak_lineBoundary_returnsLinePosition() throws IOException {
        String line = "This is a line with many words and information for testing the line splitting logic. ";
        String text = line.repeat(10) + "\n" + line.repeat(10) + "\n" + line.repeat(10);
        assertThat(text.length()).as("Text should be over 800 chars").isGreaterThan(800);
        try (ChunkWriter writer = createChunkWriter(800)) {
            int breakPos = writer.bestBreak(text, 0, 800);
            assertThat(breakPos).isGreaterThan(0);
        }
    }

    @Test
    @Order(10)
    void bestBreak_noGoodBreak_returnsHardEnd() throws IOException {
        String text = "Hello world this is a long text without many spaces so we can test the word boundary splitting logic";
        try (ChunkWriter writer = createChunkWriter(600)) {
            int breakPos = writer.bestBreak(text, 0, 600);
            assertThat(breakPos).isGreaterThan(0);
            assertThat(breakPos).isLessThanOrEqualTo(600);
        }
    }
}

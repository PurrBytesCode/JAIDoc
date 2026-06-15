package com.purrbyte.ai.doclet;

import com.purrbyte.ai.test.BaseTest;
import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag(BaseTest.TAG_UNIT)
class DocTreeJsonTest extends UnitTest {

    @Test
    @Order(1)
    void normalize_collapsesMultipleConsecutiveSpaces() {
        String result = DocTreeJson.normalize("hello   world");
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    @Order(2)
    void normalize_preservesSingleNewline() {
        String result = DocTreeJson.normalize("hello\nworld");
        assertThat(result).isEqualTo("hello\nworld");
    }

    @Test
    @Order(4)
    void normalize_trimsWhitespace() {
        String result = DocTreeJson.normalize("  hello world  ");
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    @Order(5)
    void normalize_preservesNewlineAfterTrim() {
        String result = DocTreeJson.normalize("  hello\n\nworld  ");
        // Trimming removes leading/trailing spaces and collapses consecutive newlines
        assertThat(result).isEqualTo("hello\nworld");
    }

    @Test
    @Order(6)
    void normalize_emptyString() {
        String result = DocTreeJson.normalize("");
        assertThat(result).isEqualTo("");
    }

    @Test
    @Order(7)
    void normalize_onlyWhitespace() {
        String result = DocTreeJson.normalize("   ");
        assertThat(result).isEqualTo("");
    }

    @Test
    @Order(8)
    void normalize_mixedSpacesAndNewlines() {
        // Consecutive spaces and newlines are collapsed to single spaces/newlines
        // Note: "world  " → "world " (one space after word before newline)
        String result = DocTreeJson.normalize("  hello   world  \n\n  foo  bar  ");
        assertThat(result).isEqualTo("hello world \nfoo bar");
    }

    @Test
    @Order(9)
    void normalize_consecutiveSpacesAfterNewline() {
        String result = DocTreeJson.normalize("hello\n\n  world");
        // Consecutive newlines and spaces are collapsed to single ones
        assertThat(result).isEqualTo("hello\nworld");
    }

    @Test
    @Order(10)
    void normalize_collapsesConsecutiveNewlines() {
        // The method DOES collapse consecutive newlines into a single newline
        String result = DocTreeJson.normalize("hello\n\n\nworld");
        assertThat(result).isEqualTo("hello\nworld");
    }

    @Test
    @Order(11)
    void decodeEntity_ampersand() {
        String result = DocTreeJson.decodeEntity("amp");
        assertThat(result).isEqualTo("&");
    }

    @Test
    @Order(12)
    void decodeEntity_lessThan() {
        String result = DocTreeJson.decodeEntity("lt");
        assertThat(result).isEqualTo("<");
    }

    @Test
    @Order(13)
    void decodeEntity_greaterThan() {
        String result = DocTreeJson.decodeEntity("gt");
        assertThat(result).isEqualTo(">");
    }

    @Test
    @Order(14)
    void decodeEntity_quot() {
        String result = DocTreeJson.decodeEntity("quot");
        assertThat(result).isEqualTo("\"");
    }

    @Test
    @Order(15)
    void decodeEntity_apostrophe() {
        String result = DocTreeJson.decodeEntity("apos");
        assertThat(result).isEqualTo("'");
    }

    @Test
    @Order(16)
    void decodeEntity_nonBreakingSpace() {
        String result = DocTreeJson.decodeEntity("nbsp");
        assertThat(result).isEqualTo(" ");
    }

    @Test
    @Order(17)
    void decodeEntity_copyright() {
        String result = DocTreeJson.decodeEntity("copy");
        assertThat(result).isEqualTo("©");
    }

    @Test
    @Order(18)
    void decodeEntity_registered() {
        String result = DocTreeJson.decodeEntity("reg");
        assertThat(result).isEqualTo("®");
    }

    @Test
    @Order(19)
    void decodeEntity_hyphenLong() {
        String result = DocTreeJson.decodeEntity("mdash");
        assertThat(result).isEqualTo("—");
    }

    @Test
    @Order(20)
    void decodeEntity_hyphenShort() {
        String result = DocTreeJson.decodeEntity("ndash");
        assertThat(result).isEqualTo("–");
    }

    @Test
    @Order(21)
    void decodeEntity_hellip() {
        String result = DocTreeJson.decodeEntity("hellip");
        assertThat(result).isEqualTo("…");
    }

    @Test
    @Order(22)
    void decodeEntity_hexNumeric() {
        String result = DocTreeJson.decodeEntity("#x26");
        assertThat(result).isEqualTo("&");
    }

    @Test
    @Order(23)
    void decodeEntity_decimalNumeric() {
        String result = DocTreeJson.decodeEntity("#38");
        assertThat(result).isEqualTo("&");
    }

    @Test
    @Order(24)
    void decodeEntity_unknownNamedEntity_returnsAsIs() {
        String result = DocTreeJson.decodeEntity("zzz");
        assertThat(result).isEqualTo("&zzz;");
    }

    @Test
    @Order(25)
    void decodeEntity_unknownNumericEntity_returnsAsIs() {
        // Invalid hex number — preserves the # prefix
        String result = DocTreeJson.decodeEntity("#xxyz");
        assertThat(result).isEqualTo("&#xxyz;");
    }

    @Test
    @Order(26)
    void text_withNullList_returnsEmptyString() {
        DocTreeJson dtj = new DocTreeJson(jsonMapper);
        String result = dtj.text(null);
        assertThat(result).isEqualTo("");
    }

    @Test
    @Order(27)
    void text_withEmptyList_returnsEmptyString() {
        DocTreeJson dtj = new DocTreeJson(jsonMapper);
        String result = dtj.text(java.util.List.of());
        assertThat(result).isEqualTo("");
    }
}

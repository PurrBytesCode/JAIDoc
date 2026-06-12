package com.purrbyte.ai.doclet;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code chunks.jsonl}: one JSON line per documented element with the
 * shape {@code {"id": ..., "text": ..., "metadata": {...}}}, designed to be
 * ingested directly into ChromaDB (ids → ids, text → documents,
 * metadata → metadatas; metadata values are always primitives).
 *
 * <p>If an element's text exceeds {@code maxChars}, it is split into several
 * overlapping fragments, preferring to cut at paragraph boundaries
 * ({@code \n\n}), then at line breaks, and finally at word boundaries. Each fragment
 * keeps the same id with a {@code #part/total} suffix and metadata
 * {@code part}/{@code parts} to enable reassembling results.
 */
final class ChunkWriter implements Closeable {

    private final BufferedWriter out;
    private final JsonMapper mapper;
    private final int maxChars;
    private final int overlap;
    private final boolean onlyDocumented;
    private long count = 0;

    ChunkWriter(Path file, JsonMapper mapper, int maxChars, int overlap,
                boolean onlyDocumented) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        this.mapper = mapper;
        this.maxChars = Math.max(500, maxChars);
        this.overlap = Math.max(0, Math.min(overlap, this.maxChars / 2));
        this.onlyDocumented = onlyDocumented;
    }

    void write(String id, String text, ObjectNode metadata, boolean documented) {
        if (onlyDocumented && !documented) return;
        List<String> parts = split(text);
        try {
            for (int i = 0; i < parts.size(); i++) {
                ObjectNode line = mapper.createObjectNode();
                ObjectNode meta = metadata.deepCopy();
                if (parts.size() == 1) {
                    line.put("id", id);
                } else {
                    line.put("id", id + "#" + (i + 1) + "/" + parts.size());
                    meta.put("parentId", id);
                    meta.put("part", i + 1);
                    meta.put("parts", parts.size());
                }
                line.put("text", parts.get(i));
                line.set("metadata", meta);
                out.write(mapper.writeValueAsString(line));
                out.write('\n');
                count++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    long count() {
        return count;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        if (text.length() <= maxChars) {
            parts.add(text);
            return parts;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                end = bestBreak(text, start, end);
            }
            parts.add(text.substring(start, end).trim());
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return parts;
    }

    /**
     * Finds the best cut point: paragraph > line > sentence > hard cut.
     */
    private int bestBreak(String text, int start, int hardEnd) {
        int minAcceptable = start + (hardEnd - start) / 2; // don't cut too early
        int p = text.lastIndexOf("\n\n", hardEnd);
        if (p > minAcceptable) return p;
        p = text.lastIndexOf('\n', hardEnd);
        if (p > minAcceptable) return p;
        p = text.lastIndexOf(". ", hardEnd);
        if (p > minAcceptable) return p + 1;
        p = text.lastIndexOf(' ', hardEnd);
        if (p > minAcceptable) return p;
        return hardEnd;
    }
}

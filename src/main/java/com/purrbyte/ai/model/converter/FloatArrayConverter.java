package com.purrbyte.ai.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Stores a {@code float[]} embedding as a {@code byte[]} BLOB (SQLite has no vector column type).
 */
@Converter
public class FloatArrayConverter implements AttributeConverter<float[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(attribute.length * Float.BYTES);
        buffer.asFloatBuffer().put(attribute);
        return buffer.array();
    }

    @Override
    public float[] convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        FloatBuffer floats = ByteBuffer.wrap(dbData).asFloatBuffer();
        float[] result = new float[floats.remaining()];
        floats.get(result);
        return result;
    }
}

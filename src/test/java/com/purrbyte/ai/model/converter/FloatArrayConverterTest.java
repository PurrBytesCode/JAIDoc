package com.purrbyte.ai.model.converter;

import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FloatArrayConverterTest extends UnitTest {

    @Test
    void roundTripsFloatArray() {
        FloatArrayConverter converter = new FloatArrayConverter();
        float[] original = {0.1f, -0.2f, 0.3f, 1.0f};
        float[] result = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(original));
        assertArrayEquals(original, result, 0.0f);
    }

    @Test
    void convertsNullToNull() {
        FloatArrayConverter converter = new FloatArrayConverter();
        assert converter.convertToDatabaseColumn(null) == null;
        assert converter.convertToEntityAttribute(null) == null;
    }

    @Test
    void roundTripsEmptyArray() {
        FloatArrayConverter converter = new FloatArrayConverter();
        float[] original = {};
        float[] result = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(original));
        assertArrayEquals(original, result, 0.0f);
    }

    @Test
    void roundTripsFullDimension() {
        FloatArrayConverter converter = new FloatArrayConverter();
        float[] original = new float[384];
        for (int i = 0; i < original.length; i++) {
            original[i] = (float) Math.sin(i) * i / 100.0f;
        }
        float[] result = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(original));
        assertArrayEquals(original, result, 0.0f);
    }
}

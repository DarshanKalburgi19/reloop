package com.reloop.product.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Consumer/provider contract checks against the JSON examples in the repository's top-level {@code contracts/}
 * folder. Each service keeps its own copy of the DTOs (no shared code, no shared database); these checks make
 * sure both copies describe the same wire format:
 * <ul>
 *   <li>{@link #assertSends}: a request record serialises to exactly the fixture (same fields, same values).</li>
 *   <li>{@link #assertAccepts}: the receiving DTO knows every fixture field (unknown fields fail) and the fixture
 *       passes its Bean Validation constraints.</li>
 *   <li>{@link #assertProduces}: a response record declares every fixture field with the same JSON type.</li>
 *   <li>{@link #assertReads}: a client response record only expects fields that the fixture (= the server) sends.</li>
 * </ul>
 * Kept in sync by hand in all three services; the fixture folder is the single source of truth.
 */
public final class Contracts {

    private static final ObjectMapper STRICT = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private static final ObjectMapper LENIENT = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private Contracts() {
    }

    /** Reads {@code contracts/<path>} (tests run from the module folder, so the repository root is {@code ..}). */
    public static JsonNode fixture(String path) {
        Path file = Path.of("..", "contracts", path);
        try {
            return STRICT.readTree(Files.readString(file));
        } catch (IOException ex) {
            throw new UncheckedIOException("contract fixture " + file.toAbsolutePath() + " is missing or invalid", ex);
        }
    }

    public static <T> T assertSends(String path, Class<T> requestType) {
        JsonNode expected = fixture(path);
        T value = read(STRICT, expected, requestType, path);
        JsonNode actual = STRICT.valueToTree(value);
        covers(actual, expected, path + " $");
        covers(expected, actual, path + " (extra field sent) $");
        return value;
    }

    public static <T> T assertAccepts(String path, Class<T> requestType) {
        JsonNode expected = fixture(path);
        T value = read(STRICT, expected, requestType, path);
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(value);
        assertThat(violations).as(path + " must satisfy " + requestType.getSimpleName() + "'s constraints").isEmpty();
        covers(STRICT.valueToTree(value), expected, path + " $");
        return value;
    }

    public static <T> T assertProduces(String path, Class<T> responseType) {
        JsonNode expected = fixture(path);
        T value = read(STRICT, expected, responseType, path);
        covers(STRICT.valueToTree(value), expected, path + " $");
        return value;
    }

    public static <T> T assertReads(String path, Class<T> responseType) {
        JsonNode sent = fixture(path);
        T value = read(LENIENT, sent, responseType, path);
        JsonNode tree = LENIENT.valueToTree(value);
        // only the record components: helper methods such as isConfirmed() are not part of the wire format
        for (RecordComponent component : responseType.getRecordComponents()) {
            String name = component.getName();
            assertThat(sent.has(name)).as(path + ": the client expects '" + name + "' but the server doesn't send it")
                    .isTrue();
            covers(tree.get(name), sent.get(name), path + " $." + name);
        }
        return value;
    }

    private static <T> T read(ObjectMapper mapper, JsonNode json, Class<T> type, String path) {
        try {
            return mapper.treeToValue(json, type);
        } catch (IOException ex) {
            return fail(path + " doesn't map onto " + type.getName() + ": " + ex.getMessage());
        }
    }

    /** Every field of {@code expected} exists in {@code actual} with an equal value (numbers compared numerically). */
    private static void covers(JsonNode actual, JsonNode expected, String where) {
        if (expected.isObject()) {
            assertThat(actual.isObject()).as(where + " must be an object").isTrue();
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                assertThat(actual.has(f.getKey())).as(where + "." + f.getKey() + " is missing").isTrue();
                covers(actual.get(f.getKey()), f.getValue(), where + "." + f.getKey());
            }
        } else if (expected.isArray()) {
            assertThat(actual.isArray() && actual.size() == expected.size()).as(where + " array size").isTrue();
            for (int i = 0; i < expected.size(); i++) {
                covers(actual.get(i), expected.get(i), where + "[" + i + "]");
            }
        } else if (expected.isNumber()) {
            assertThat(actual.isNumber()).as(where + " must be a number but is " + actual).isTrue();
            assertThat(actual.decimalValue().compareTo(expected.decimalValue())).as(where + ": " + actual + " vs " + expected)
                    .isZero();
        } else {
            assertThat(actual).as(where).isEqualTo(expected);
        }
    }
}

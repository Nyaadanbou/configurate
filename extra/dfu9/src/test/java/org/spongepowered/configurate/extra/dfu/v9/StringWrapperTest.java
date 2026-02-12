package org.spongepowered.configurate.extra.dfu.v9;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class StringWrapperTest {

    @Test
    void testRoundTrip1() throws Exception {
        // Create a custom TypeSerializerCollection
        final TypeSerializerCollection serializers = TypeSerializerCollection.defaults().childBuilder()
                .register(StringWrapper.class, new StringWrapperScalarSerializer())
                .build();

        // Write data to ConfigurationNode
        final BasicConfigurationNode node = BasicConfigurationNode.root(
                ConfigurationOptions.defaults().serializers(serializers)
        );
        node.node("str").set(new StringWrapper("Hello! DFU8"));

        // Read the ConfigurationNode
        final StringWrapper fromNode = node.node("str").get(StringWrapper.class);
        assertEquals("Hello! DFU8", fromNode.value());

        // Test the round-trip using the DfuSerializers
        final Codec<StringWrapper> codec = DfuSerializers.codec(TypeToken.get(StringWrapper.class), serializers);
        final DataResult<JsonElement> encoded = codec.encode(fromNode, JsonOps.INSTANCE, JsonOps.INSTANCE.empty());
        final StringWrapper decoded = codec.decode(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow().getFirst();
        assertEquals("Hello! DFU8", decoded.value());
    }

    // Keep this failing test to expose the flaw of current implementation.
    //
    // This test fails due to the fact that the ObjectWrapperScalarSerializer is a
    // ScalarSerializer for a parameterized type but the actual type parameter is impossible
    // to determine at runtime when doing `Codec#encode`, where the codec is created with
    // `DfuSerializers.codec`.
    @Test
    void testRoundTrip2() throws Exception {
        // Create a custom TypeSerializerCollection
        final TypeSerializerCollection serializers = TypeSerializerCollection.defaults().childBuilder()
                .register(new TypeToken<ObjectWrapper<String>>() {}, new ObjectWrapperScalarSerializer<>()) // use ScalarSerializer
                .build();

        // Write data to ConfigurationNode
        final BasicConfigurationNode node = BasicConfigurationNode.root(
                ConfigurationOptions.defaults().serializers(serializers)
        );
        final BasicConfigurationNode objNode = node.node("obj");
        objNode.set(new ObjectWrapper<>("Hello!! DFU8"));

        // Read the ConfigurationNode
        final ObjectWrapper<String> fromNode = node.node("obj").get(new TypeToken<>() {});
        assertEquals("Hello!! DFU8", fromNode.value());

        // Test the round-trip using the DfuSerializers
        final Codec<ObjectWrapper<String>> codec = DfuSerializers.codec(new TypeToken<>() {}, serializers);
        final DataResult<JsonElement> encoded = codec.encode(fromNode, JsonOps.INSTANCE, JsonOps.INSTANCE.empty());
        final ObjectWrapper<String> decoded = codec.decode(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow().getFirst();
        assertEquals("Hello!! DFU8", decoded.value());
    }

    @Test
    void testRoundTrip3() throws Exception {
        // Create a custom TypeSerializerCollection
        final TypeSerializerCollection serializers = TypeSerializerCollection.defaults().childBuilder()
                .register(new TypeToken<ObjectWrapper<String>>() {}, new ObjectWrapperTypeSerializer<>()) // use TypeSerializer
                .build();

        // Write data to ConfigurationNode
        final BasicConfigurationNode node = BasicConfigurationNode.root(
                ConfigurationOptions.defaults().serializers(serializers)
        );
        BasicConfigurationNode objNode = node.node("obj");
        objNode.set(new ObjectWrapper<>("Hello!!! DFU8"));

        // Read the ConfigurationNode
        final ObjectWrapper<String> fromNode = node.node("obj").get(new TypeToken<>() {});
        assertEquals("Hello!!! DFU8", fromNode.value());

        // Test the round-trip using the DfuSerializers
        final Codec<ObjectWrapper<String>> codec = DfuSerializers.codec(new TypeToken<>() {}, serializers);
        final DataResult<JsonElement> encoded = codec.encode(fromNode, JsonOps.INSTANCE, JsonOps.INSTANCE.empty());
        final ObjectWrapper<String> decoded = codec.decode(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow().getFirst();
        assertEquals("Hello!!! DFU8", decoded.value());
    }

    public record StringWrapper(String value) {}

    public record ObjectWrapper<T>(T value) {}

    /*public final class StringWrapperCodec implements Codec<StringWrapper> {

        @Override
        public <T> DataResult<Pair<StringWrapper, T>> decode(final DynamicOps<T> ops, final T input) {
            return ops.getStringValue(input).map(str -> Pair.of(new StringWrapper(str), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(final StringWrapper input, final DynamicOps<T> ops, final T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createString(input.value()));
        }
    }*/

    public static final class StringWrapperScalarSerializer extends ScalarSerializer<StringWrapper> {

        public StringWrapperScalarSerializer() {
            super(StringWrapper.class);
        }

        @Override
        public StringWrapper deserialize(Type type, Object obj) throws SerializationException {
            final String raw = obj.toString();
            if (raw == null) {
                throw new SerializationException("No string content");
            }
            return new StringWrapper(raw);
        }

        @Override
        protected Object serialize(StringWrapper item, Predicate<Class<?>> typeSupported) {
            return item.value();
        }
    }

    public static final class ObjectWrapperScalarSerializer<T> extends ScalarSerializer<ObjectWrapper<T>> {
        public ObjectWrapperScalarSerializer() {
            super(new TypeToken<>() {});
        }

        @Override
        public ObjectWrapper<T> deserialize(final Type type, final Object obj) throws SerializationException {
            if (obj == null) {
                throw new SerializationException("No content");
            }
            return new ObjectWrapper<>((T) obj);
        }

        @Override
        protected Object serialize(final ObjectWrapper<T> item, final Predicate<Class<?>> typeSupported) {
            return item.value();
        }
    }

    public static final class ObjectWrapperTypeSerializer<T> implements TypeSerializer<ObjectWrapper<T>> {

        @Override
        public ObjectWrapper<T> deserialize(Type type, ConfigurationNode node) {
            return new ObjectWrapper(node.rawScalar());
        }

        @Override
        public void serialize(Type type, @Nullable ObjectWrapper<T> obj, ConfigurationNode node) throws SerializationException {
            if (obj == null) return;
            node.set(obj.value());
        }
    }

}

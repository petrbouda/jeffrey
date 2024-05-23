package pbouda.jeffrey.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class TypeDeserializer extends StdDeserializer<Type> {

    public TypeDeserializer() {
        this(null);
    }

    public TypeDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Type deserialize(JsonParser jp, DeserializationContext context) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node == null) {
            throw new NullPointerException("Type is null");
        }

        String typeCode = node.asText();
        return Type.getKnownType(typeCode)
                .orElseGet(() -> new Type(typeCode));
    }
}

package pbouda.jeffrey.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class TypeSerializer extends StdSerializer<Type> {

    public TypeSerializer() {
        super(Type.class);
    }

    protected TypeSerializer(Class<Type> t) {
        super(t);
    }

    @Override
    public void serialize(Type type, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
        gen.writeString(type.code());
    }
}

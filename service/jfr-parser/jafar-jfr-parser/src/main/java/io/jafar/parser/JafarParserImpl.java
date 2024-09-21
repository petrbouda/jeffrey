

package io.jafar.parser;

import io.jafar.parser.api.*;
import io.jafar.parser.api.types.JFRHandler;
import io.jafar.parser.internal_api.*;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataEvent;
import io.jafar.parser.internal_api.metadata.MetadataField;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public final class JafarParserImpl implements JafarParser {
    private final class HandlerRegistrationImpl<T> implements HandlerRegistration<T> {
        private final WeakReference<Class<T>> clzRef;
        private final WeakReference<JafarParser> cookieRef;
        HandlerRegistrationImpl(Class<T> clz, JafarParser cookie) {
            this.clzRef = new WeakReference<>(clz);
            this.cookieRef = new WeakReference<>(cookie);
        }

        @Override
        public void destroy(JafarParser cookie) {
            if (cookie != null && cookie.equals(cookieRef.get())) {
                Class<?> clz = clzRef.get();
                if (clz != null) {
                    handlerMap.remove(clz);
                    globalDeserializerMap.clear();
                    handlerMap.keySet().forEach(JafarParserImpl.this::addDeserializer);
                    chunkHandlerMethodMap.forEach((i, map) -> {
                        map.remove(clz);
                    });
                }
            }
        }
    }
    private final StreamingChunkParser parser;
    private final Path recording;

    private final Map<Class<?>, List<JFRHandler.Impl<?>>> handlerMap = new HashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<Class<?>>> chunkTypeClassMap = new Int2ObjectOpenHashMap<>();

    private final Map<String, io.jafar.parser.JFRValueDeserializer<?>> globalDeserializerMap = new HashMap<>();
    private final Int2ObjectMap<Map<Class<?>, MethodHandle>> chunkHandlerMethodMap = new Int2ObjectOpenHashMap<>();

    private final ThreadLocal<Long2ObjectMap<Class<?>>> typeClassMapRef = new ThreadLocal<>();
    private final ThreadLocal<Map<Class<?>, MethodHandle>> handlerMethodMapRef = new ThreadLocal<>();

    private boolean closed = false;

    public JafarParserImpl(Path recording) {
        this.parser = new StreamingChunkParser();
        this.recording = recording;
    }

    @Override
    public <T> HandlerRegistration<T> handle(Class<T> clz, JFRHandler<T> handler) {
        addDeserializer(clz);
        handlerMap.computeIfAbsent(clz, k -> new ArrayList<>()).add(new JFRHandler.Impl<>(clz, handler));

        return new HandlerRegistrationImpl<>(clz, this);
    }

    private void addDeserializer(Class<?> clz) {
        if (clz.isArray()) {
            clz = clz.getComponentType();
        }
        boolean isPrimitive = clz.isPrimitive() || clz.isAssignableFrom(String.class);

        if (!isPrimitive && !clz.isInterface()) {
            throw new RuntimeException("JFR type handler must be an interface: " + clz.getName());
        }
        String typeName = clz.getName();
        if (!isPrimitive) {
            JfrType typeAnnotation = clz.getAnnotation(JfrType.class);
            if (typeAnnotation == null) {
                throw new RuntimeException("JFR type annotation missing on class: " + clz.getName());
            }
            typeName = typeAnnotation.value();
        }

        if (globalDeserializerMap.containsKey(typeName)) {
            return;
        }
        globalDeserializerMap.put(typeName, io.jafar.parser.JFRValueDeserializer.create(clz));
        if (!isPrimitive) {
            Class<?> superClass = clz.getSuperclass();
            if (superClass != null && superClass.isInterface()) {
                addDeserializer(superClass);
            }
            for (Method m : clz.getMethods()) {
                if (m.getAnnotation(JfrIgnore.class) == null) {
                    addDeserializer(m.getReturnType());
                }
            }
        }
    }

    private Set<String> collectUsedAttributes(Class<?> clz, Map<String, String> fieldToMethodMap) {
        Set<String> usedAttributes = new HashSet<>();
        Class<?> c = clz;
        while (c != null) {
            usedAttributes.addAll(Arrays.stream(c.getMethods())
                    .filter(m -> m.getAnnotation(JfrIgnore.class) == null)
                    .map(m -> {
                        String name = m.getName();
                        JfrField fieldAnnotation = m.getAnnotation(JfrField.class);
                        if (fieldAnnotation != null) {
                            name = fieldAnnotation.value();
                            fieldToMethodMap.put(name, m.getName());
                        }
                        return name;
                    })
                    .collect(Collectors.toSet()));
            Class<?> superClz = c.getSuperclass();
            if (superClz != null && superClz.isInterface()) {
                c = superClz;
            } else {
                c = null;
            }
        }
        return usedAttributes;
    }

    private MethodHandle getHandlerMethod(int chunk, MetadataClass mdClass, Class<?> clz, Long2ObjectMap<Class<?>> typeClassMap) {
        Map<Class<?>, MethodHandle> handlerMethodMap = handlerMethodMapRef.get();

        Map<String, String> fieldToMethodMap = new HashMap<>();
        MethodHandle mh = handlerMethodMap.get(clz);
        if (mh != null) {
            return mh;
        }
        try {
            if (clz == int.class || clz == Integer.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(int.class, RecordingStream.class));
            } else if (clz == long.class || clz == Long.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class));
            } else if (clz == short.class || clz == Short.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(short.class));
            } else if (clz == char.class || clz == Character.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(char.class));
            } else if (clz == byte.class || clz == Byte.class) {
                mh = MethodHandles.explicitCastArguments(MethodHandles.lookup().findVirtual(RecordingStream.class, "readVarint", MethodType.methodType(long.class)), MethodType.methodType(byte.class));
            } else if (clz == double.class || clz == Double.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readDouble", MethodType.methodType(double.class));
            } else if (clz == float.class || clz == Float.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readFloat", MethodType.methodType(float.class));
            } else if (clz == boolean.class || clz == Boolean.class) {
                mh = MethodHandles.lookup().findVirtual(RecordingStream.class, "readBoolean", MethodType.methodType(boolean.class));
            } else if (clz == String.class) {
                mh = MethodHandles.lookup().findStatic(ParsingUtils.class, "readUTF8", MethodType.methodType(String.class, RecordingStream.class));
            } else {
                if (!clz.isInterface()) {
                    throw new RuntimeException("Unsupported type: " + clz.getName());
                }
                String clzName = JafarParserImpl.class.getPackage().getName() + "." + clz.getSimpleName() + "$" + chunk;
                // generate handler class
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, clzName.replace('.', '/'), null, "java/lang/Object", new String[]{clz.getName().replace('.', '/')});
                cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "context", Type.getDescriptor(ParserContext.class), null, null).visitEnd();

                Set<String> usedAttributes = collectUsedAttributes(clz, fieldToMethodMap);

                Deque<MetadataClass> stack = new ArrayDeque<>();
                stack.push(mdClass);
                // TODO ignore inheritence for now
//                        while (true) {
//                            MetadataClass superMd = context.getMetadataLookup().;
//                            String superName = mdClass.getSuperType();
//                            if (superName != null) {
//                                stack.push(context.getMetadataLookup().getClass(mdClass.getId()));
//                            } else {
//                                break;
//                            }
//                        }

                List<MetadataField> allFields = new ArrayList<>();
                Set<MetadataField> appliedFields = new HashSet<>();
                while (!stack.isEmpty()) {
                    MetadataClass current = stack.pop();
                    for (MetadataField field : current.getFields()) {
                        String fieldName = field.getName();
                        allFields.add(field);
                        if (!usedAttributes.contains(fieldName)) {
                            continue;
                        }
                        appliedFields.add(field);

                        Class<?> fldClz = typeClassMap.get(field.getType().getId());
                        boolean withConstantPool = field.hasConstantPool();
                        String methodName = fieldToMethodMap.get(fieldName);
                        if (methodName == null) {
                            methodName = fieldName;
                        }
                        if (withConstantPool) {
                            CodeGenerator.handleFieldRef(cw, clzName, field, fldClz, fieldName + "_ref", methodName);
                        } else {
                            CodeGenerator.handleField(cw, clzName, field, fldClz, fieldName, methodName);
                        }
                    }
                    CodeGenerator.prepareConstructor(cw, clzName, current, allFields, appliedFields, typeClassMap);
                }
                cw.visitEnd();
                byte[] classData = cw.toByteArray();

                Files.write(Paths.get("/tmp/"+ clz.getSimpleName() + ".class"), classData);

                MethodHandles.Lookup lkp = MethodHandles.lookup().defineHiddenClass(classData, true, MethodHandles.Lookup.ClassOption.NESTMATE);
                mh = lkp.findConstructor(lkp.lookupClass(), MethodType.methodType(void.class, RecordingStream.class));
            }
            return mh;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void run() throws IOException {
        if (closed) {
            throw new IOException("Parser is closed");
        }
        // parse JFR and run handlers
        parser.parse(openJfrStream(recording), new ChunkParserListener() {
            private final Control ctl = new Control();

            @Override
            public void onRecordingStart(ParserContext context) {
                if (!globalDeserializerMap.isEmpty()) {
                    context.setTypeFilter(t -> globalDeserializerMap.containsKey(t.getName()));
                }
            }

            @Override
            public boolean onChunkStart(int chunkIndex, ChunkHeader header, ParserContext context) {
                if (!globalDeserializerMap.isEmpty()) {
                    synchronized (this) {
                        typeClassMapRef.set(chunkTypeClassMap.computeIfAbsent(chunkIndex, k -> new Long2ObjectOpenHashMap<>()));
                        handlerMethodMapRef.set(chunkHandlerMethodMap.computeIfAbsent(chunkIndex, k -> new HashMap<>()));

                        io.jafar.parser.Deserializers target = context.getDeserializers();
                        for (Map.Entry<String, io.jafar.parser.JFRValueDeserializer<?>> e : globalDeserializerMap.entrySet()) {
                            target.putIfAbsent(e.getKey(), e.getValue().duplicate());
                        }
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onChunkEnd(int chunkIndex, boolean skipped) {
                typeClassMapRef.remove();
                handlerMethodMapRef.remove();
                return true;
            }

            @Override
            public boolean onMetadata(MetadataEvent metadata) {
                Long2ObjectMap<Class<?>> typeClassMap = typeClassMapRef.get();

                io.jafar.parser.Deserializers deserializers = metadata.getContext().getDeserializers();
                // typeClassMap must be fully intialized before trying to resolve/generate the handlers
                for (MetadataClass clz : metadata.getClasses()) {
                    io.jafar.parser.JFRValueDeserializer<?> deserializer = (io.jafar.parser.JFRValueDeserializer<?>) deserializers.getDeserializer(clz.getName());
                    if (deserializer != null) {
                        typeClassMap.putIfAbsent(clz.getId(), deserializer.getClazz());
                    }
                }
                for (MetadataClass clz : metadata.getClasses()) {
                    io.jafar.parser.JFRValueDeserializer<?> deserializer = (JFRValueDeserializer<?>) deserializers.getDeserializer(clz.getName());
                    if (deserializer != null) {
                        deserializer.setHandler(getHandlerMethod(metadata.getContext().getChunkIndex(), clz, deserializer.getClazz(), typeClassMap));
                    }
                }

                return true;
            }

            @Override
            public boolean onCheckpoint(CheckpointEvent checkpoint) {
                return ChunkParserListener.super.onCheckpoint(checkpoint);
            }

            @Override
            public boolean onEvent(long typeId, RecordingStream stream, long payloadSize) {
                Deserializers deserializers = stream.getContext().getDeserializers();
                Long2ObjectMap<Class<?>> typeClassMap = typeClassMapRef.get();
                Class<?> typeClz = typeClassMap.get(typeId);
                if (typeClz == null) {
                    return true;
                }
                if (handlerMap.containsKey(typeClz)) {
                    String typeName = typeClz.getAnnotation(JfrType.class).value();
                    DeserializationHandler<?> deserializer = deserializers.getDeserializer(typeName);
                    if (deserializer != null) {
                        Object deserialized = deserializer.handle(stream);
                        for (JFRHandler.Impl<?> handler : handlerMap.get(typeClz)) {
                            handler.handle(deserialized, null);
                        }
                    }
                }

                return true;
            };
        });
    }

    @Override
    public void close() throws Exception {
        if (!closed) {
            closed = true;

            parser.close();
            chunkTypeClassMap.clear();
            chunkHandlerMethodMap.clear();
            handlerMap.clear();
            globalDeserializerMap.clear();
        }
    }

    private static ByteBuffer openJfrStream(Path jfrFile) {
        try (RandomAccessFile raf = new RandomAccessFile(jfrFile.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}



package io.jafar.parser;

import io.jafar.parser.Deserializers;
import io.jafar.parser.ValueLoader;
import io.jafar.parser.internal_api.*;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataField;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.objectweb.asm.*;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

final class CodeGenerator {
    private static void castAndUnbox(MethodVisitor mv, Class<?> clz) {
        if (!clz.isPrimitive()) {
            throw new RuntimeException("Not a primitive type: " + clz.getName());
        }
        if (clz == int.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Integer.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Integer.class), "intValue", Type.getMethodDescriptor(Type.INT_TYPE));
        } else if (clz == long.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Long.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Long.class), "longValue", Type.getMethodDescriptor(Type.LONG_TYPE));
        } else if (clz == short.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Short.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Short.class), "shortValue", Type.getMethodDescriptor(Type.SHORT_TYPE));
        } else if (clz == char.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Character.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Character.class), "charValue", Type.getMethodDescriptor(Type.CHAR_TYPE));
        } else if (clz == byte.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Byte.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Byte.class), "byteValue", Type.getMethodDescriptor(Type.BYTE_TYPE));
        } else if (clz == double.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Double.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Double.class), "doubleValue", Type.getMethodDescriptor(Type.DOUBLE_TYPE));
        } else if (clz == float.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Float.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Float.class), "floatValue", Type.getMethodDescriptor(Type.FLOAT_TYPE));
        } else if (clz == boolean.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Boolean.class));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Boolean.class), "booleanValue", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
        } else {
            throw new RuntimeException("Unsupported primitive type: " + clz.getName());
        }
    }

    private static void addLog(MethodVisitor mv, String msg) {
        if (false) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
            mv.visitLdcInsn(msg);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
        }
    }

    static void handleFieldRef(ClassVisitor cv, String clzName, MetadataField field, Class<?> fldType, String fldRefName, String methodName) {
        boolean isArray = field.getDimension() > 0;
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fldRefName, (isArray ? "[" : "") + "J", null, null).visitEnd();
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, methodName, "()" + (isArray ? "[" : "") + Type.getDescriptor(fldType), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), "context", Type.getDescriptor(ParserContext.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ParserContext.class), "getConstantPools", Type.getMethodDescriptor(Type.getType(ConstantPools.class)), false);
        mv.visitLdcInsn(field.getTypeId());
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ConstantPools.class), "getConstantPool", Type.getMethodDescriptor(Type.getType(ConstantPool.class), Type.LONG_TYPE), true);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        if (isArray) {
            mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), fldRefName, "[" + Type.LONG_TYPE.getDescriptor()); // [fld]
            mv.visitInsn(Opcodes.DUP); // [fld, fld]
            mv.visitVarInsn(Opcodes.ASTORE, 2); // [fld]
            mv.visitInsn(Opcodes.ARRAYLENGTH); // [int]
            mv.visitInsn(Opcodes.DUP); // [int, int]
            mv.visitVarInsn(Opcodes.ISTORE, 3); // [int]
            mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(fldType)); // [array]
            mv.visitLdcInsn(0); // [array, int]
            mv.visitVarInsn(Opcodes.ISTORE, 4); // [array]
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitLabel(l1);
            mv.visitVarInsn(Opcodes.ILOAD, 3); // [array, int]
            mv.visitVarInsn(Opcodes.ILOAD, 4); // [array, int, int]
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [array]
            mv.visitInsn(Opcodes.DUP); // [array, array]
            mv.visitVarInsn(Opcodes.ILOAD, 4); // [array, array, int]
            mv.visitInsn(Opcodes.DUP); // [array, array, int, int]
            mv.visitVarInsn(Opcodes.ALOAD, 1); // [array, array, int, int, cp]
            mv.visitInsn(Opcodes.SWAP); // [array, array, int, cp, int]
            mv.visitVarInsn(Opcodes.ALOAD, 2); // [array, array, int, cp, int, fld]
            mv.visitInsn(Opcodes.SWAP); // [array, array, int, cp, fld, int]
            mv.visitInsn(Opcodes.LALOAD); // [array, array, int, cp, long]
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ConstantPool.class), "get", Type.getMethodDescriptor(Type.getType(Object.class), Type.LONG_TYPE), true); // [array, array, int, obj]
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldType)); // [array, array, int, fldval]
            mv.visitInsn(Opcodes.AASTORE); // [array]
            mv.visitIincInsn(4, 1); // [array]
            mv.visitJumpInsn(Opcodes.GOTO, l1);
            mv.visitLabel(l2);
        } else {
            mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), fldRefName, Type.LONG_TYPE.getDescriptor());
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ConstantPool.class), "get", Type.getMethodDescriptor(Type.getType(Object.class), Type.LONG_TYPE), true);
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldType));
        }
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static void handleField(ClassVisitor cv, String clzName, MetadataField field, Class<?> fldType, String fieldName, String methodName) {
        boolean isArray = field.getDimension() > 0;
        String fldDescriptor = (isArray ? "[" : "") + Type.getDescriptor(fldType);
        cv.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fieldName, fldDescriptor, null, null).visitEnd();
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, methodName, "()" + (isArray ? "[" : "") + Type.getDescriptor(fldType), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
        mv.visitFieldInsn(Opcodes.GETFIELD, clzName.replace('.', '/'), fieldName, fldDescriptor); // [fld]
        if (isArray) {
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitInsn(Type.getType(fldType).getOpcode(Opcodes.IRETURN));
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static void prepareConstructor(ClassVisitor cv, String clzName, MetadataClass clz, List<MetadataField> allFields, Set<MetadataField> appliedFields, Long2ObjectMap<Class<?>> typeClassMap) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class)), null, null);
        mv.visitCode();
        int contextIdx = 2;
        int deserializersIdx = 3;
        int meteadataIdx = 4;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
        // store context field
        addLog(mv, "Reading object of type: " + clz.getName());
        mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
        mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, pc]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "getContext", Type.getMethodDescriptor(Type.getType(ParserContext.class)), false); // [this, ctx]
        mv.visitInsn(Opcodes.DUP); // [this, ctx, ctx]
        mv.visitVarInsn(Opcodes.ASTORE, contextIdx); // [this, ctx]
        mv.visitInsn(Opcodes.DUP); // [this, ctx, ctx]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ParserContext.class), "getDeserializers", Type.getMethodDescriptor(Type.getType(io.jafar.parser.Deserializers.class)), false); // [this, ctx, deserializers]
        mv.visitVarInsn(Opcodes.ASTORE, deserializersIdx); // [this, ctx]
        mv.visitInsn(Opcodes.DUP); // [this, ctx, ctx]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(ParserContext.class), "getMetadataLookup", Type.getMethodDescriptor(Type.getType(MetadataLookup.class)), false); // [this, ctx, metadata]
        mv.visitVarInsn(Opcodes.ASTORE, meteadataIdx); // [this, ctx]
        mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), "context", Type.getDescriptor(ParserContext.class)); // []

        for (MetadataField fld : allFields) {
            ;
            boolean withConstantPool = fld.hasConstantPool(); // || fld.getType().getName().equals("java.lang.String");
            if (!appliedFields.contains(fld)) {
                // skip
                addLog(mv, "Skipping field: " + fld.getName());
                mv.visitVarInsn(Opcodes.ALOAD, 1); // [stream]
                mv.visitVarInsn(Opcodes.ALOAD, meteadataIdx); // [stream, metadata]
                mv.visitLdcInsn(fld.getTypeId()); // [stream, metadata, long, long]
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(MetadataLookup.class), "getClass", Type.getMethodDescriptor(Type.getType(MetadataClass.class), Type.LONG_TYPE), true); // [stream, metadata, class]
                mv.visitLdcInsn(fld.getDimension() > 0 ? 1 : 0); // [stream, metadata, class, boolean]
                mv.visitLdcInsn(withConstantPool ? 1 : 0); // [stream, metadata, class, boolean, boolean]
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ValueLoader.class), "skip", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(RecordingStream.class), Type.getType(MetadataClass.class), Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE), false); // []
                continue;
            }
            if (withConstantPool) {
                String fldRefName = fld.getName() + "_ref";
                if (fld.getDimension() > 0) {
                    int arraySizeIdx = 5;
                    int arrayCounterIdx = 6;
                    Label l1 = new Label();
                    Label l2 = new Label();
                    addLog(mv, "Reading array of refs for field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                    mv.visitInsn(Opcodes.L2I); // [this, int]
                    mv.visitInsn(Opcodes.DUP); // [this, int, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG); // [this, array]
                    mv.visitLdcInsn(0); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
                    mv.visitLabel(l1);
                    mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, int, int]
                    mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [this, array]
                    mv.visitInsn(Opcodes.DUP); // [this, array, array]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, array, array, int, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, array, array, int, long]
                    mv.visitInsn(Opcodes.LASTORE); // [this, array]
                    mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
                    mv.visitJumpInsn(Opcodes.GOTO, l1); // [this, array]
                    mv.visitLabel(l2);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fldRefName, "[" + Type.LONG_TYPE.getDescriptor()); // []
                } else {
                    addLog(mv, "Reading ref for field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fldRefName, Type.LONG_TYPE.getDescriptor()); // []
                }
            } else {
                Class<?> fldClz = typeClassMap.get(fld.getType().getId());
                if (fldClz == null) {
                    throw new RuntimeException("Unknown field type: " + fld.getType().getName());
                }
                if (fld.getDimension() > 0) {
                    int arraySizeIdx = 5;
                    int arrayCounterIdx = 6;
                    Label l1 = new Label();
                    Label l2 = new Label();
                    addLog(mv, "Reading array field: " + fld.getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RecordingStream.class), "readVarint", Type.getMethodDescriptor(Type.LONG_TYPE), false); // [this, long]
                    mv.visitInsn(Opcodes.L2I); // [this, int]
                    mv.visitInsn(Opcodes.DUP); // [this, int, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arraySizeIdx); // [this, int]
                    mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(fldClz)); // [this, array]
                    mv.visitLdcInsn(0); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ISTORE, arrayCounterIdx); // [this, array]
                    mv.visitLabel(l1);
                    mv.visitVarInsn(Opcodes.ILOAD, arraySizeIdx); // [this, array, int]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, int, int]
                    mv.visitJumpInsn(Opcodes.IF_ICMPEQ, l2); // [this, array]
                    mv.visitInsn(Opcodes.DUP); // [this, array, array]
                    mv.visitVarInsn(Opcodes.ILOAD, arrayCounterIdx); // [this, array, array, int]
                    mv.visitVarInsn(Opcodes.ALOAD, deserializersIdx); // [this, array, array, int, deserializers]
                    mv.visitLdcInsn(fld.getType().getName()); // [this, array, array, int, deserializers, name]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(io.jafar.parser.Deserializers.class), "getDeserializer", Type.getMethodDescriptor(Type.getType(DeserializationHandler.class), Type.getType(String.class)), false); // [this, array, array, int, deserializer]
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, array, array, int, deserializer, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(DeserializationHandler.class), "handle", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(RecordingStream.class)), true); // [this, array, array, int, obj]
                    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldClz)); // [this, array, array, int, fldval]
                    mv.visitInsn(Opcodes.AASTORE); // [this, array]
                    mv.visitIincInsn(arrayCounterIdx, 1); // [this, array]
                    mv.visitJumpInsn(Opcodes.GOTO, l1); // [this, array]
                    mv.visitLabel(l2);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fld.getName(), "[" + Type.getDescriptor(fldClz)); // []
                } else {
                    addLog(mv, "Reading field: " + fld.getName() + ":" + fld.getType().getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // [this]
                    mv.visitVarInsn(Opcodes.ALOAD, deserializersIdx); // [this, deserializers]
                    mv.visitLdcInsn(fld.getType().getName()); // [this, deserializers, nameg]
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Deserializers.class), "getDeserializer", Type.getMethodDescriptor(Type.getType(DeserializationHandler.class), Type.getType(String.class)), false); // [this, deserializer]
                    addLog(mv, "Got deserializers for " + fld.getType().getName());
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // [this, deserializer, stream]
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(DeserializationHandler.class), "handle", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(RecordingStream.class)), true); // [this, obj]
                    addLog(mv, "Got deserializer");
                    if (!fldClz.isPrimitive()) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fldClz)); // [this, fldval]
                    } else {
                        castAndUnbox(mv, fldClz); // [this, fldval]
                    }
                    mv.visitFieldInsn(Opcodes.PUTFIELD, clzName.replace('.', '/'), fld.getName(), Type.getDescriptor(fldClz)); // []
                }
            }
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}

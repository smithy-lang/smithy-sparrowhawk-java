/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.blobListEncodedSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.byteListLengthEncodedSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeEightBListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeFourBListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeLenPrefixedListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeVarintListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.intSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.lenPrefixedListLengthEncodedSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.longSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.sparseBlobListSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.sparseObjectListSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.uintSize;
import static software.amazon.smithy.sparrowhawk.codegen.Util.isStructure;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

final class CollectionGenerator implements Runnable {
    private final Shape shape;
    private final Model model;
    private final SymbolProvider symbolProvider;
    private final JavaWriter writer;
    private final SparrowhawkSettings settings;
    private final Symbol flyweight;
    private final Symbol userSymbol;

    CollectionGenerator(
        Shape shape,
        Model model,
        SymbolProvider symbolProvider,
        JavaWriter writer,
        SparrowhawkSettings settings
    ) {
        this.shape = shape;
        this.model = model;
        this.symbolProvider = symbolProvider;
        this.writer = writer;
        this.settings = settings;
        this.userSymbol = symbolProvider.toSymbol(shape);
        this.flyweight = userSymbol.expectProperty("generatedCollection", Symbol.class);
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext(CommonSymbols.defaultReferences());
        writer.putContext("className", flyweight.getName());
        writer.putContext("userType", userSymbol);
        if (shape.isMapShape()) {
            generateMap((MapShape) shape);
        } else if (shape.hasTrait(UniqueItemsTrait.class)) {
            generateSet((ListShape) shape);
        } else {
            generateList((ListShape) shape);
        }
        writer.popState();
    }

    private void generateList(ListShape list) {
        Shape member = model.expectShape(list.getMember().getTarget());
        ElementCodec elementCodec = codecFor(member);
        ElementCodec codec = list.hasTrait(SparseTrait.class)
            ? new SparseElementCodec(elementCodec)
            : elementCodec;
        writer.putContext("elementType", symbolProvider.toSymbol(member));

        writer.write("public final class ${className:L} implements ${sparrowhawkObject:T} {");
        writer.indent();
        writer.write("""
            private static final Object[] EMPTY = new Object[0];

            private Object[] values = EMPTY;
            private int size = -1;
            """);

        writer.openBlock("public static ${className:L} fromList(${userType:T} list) {", "}\n", () -> {
            writer.write("""
                ${className:L} l = new ${className:L}();
                int len = list.size();
                if (len == 0) {
                    l.values = EMPTY;
                    l.size = 0;
                    return l;
                }
                Object[] values = new Object[len];
                int size = 0;
                int i = 0;""");
            writer.openBlock("for (${elementType:T} e : list) {", "}", () -> {
                codec.emitFromUser("e", "values[i++]");
            });
            writer.write("""
                l.values = values;
                l.size = size;
                return l;""");
        });

        writer.openBlock("public ${userType:T} toList() {", "}\n", () -> {
            writer.write("""
                Object[] values = this.values;
                ${userType:T} list = new ${arrayList:T}<>(values.length);""");
            writer.openBlock("for (int i = 0; i < values.length; i++) {", "}", () -> {
                writer.write("list.add($L);", codec.toUserExpr("values[i]"));
            });
            writer.write("return list;");
        });

        writer.write("""
            public int elementCount() {
                return values.length;
            }
            """);

        writer.write("@Override");
        writer.openBlock("public void decodeFrom(${sparrowhawkDeserializer:T} d) {", "}\n", () -> {
            writer.write("""
                int count = $T(d.varUL());
                if (count <= 0) {
                    this.values = EMPTY;
                    this.size = 0;
                    return;
                }
                d.checkElementCount(count);
                Object[] values = new Object[count];
                int start = d.pos();""", decodeLenPrefixedListLengthChecked);
            writer.openBlock("for (int i = 0; i < count; i++) {", "}", () -> codec.emitDecode("values[i]"));
            writer.write("""
                this.values = values;
                this.size = d.pos() - start;""");
        });

        writer.write("@Override");
        writer.openBlock("public void encodeTo(${sparrowhawkSerializer:T} s) {", "}\n", () -> {
            writer.write("""
                Object[] values = this.values;
                s.writeVarUL($T(values.length));""", encodeLenPrefixedListLength);
            writer.openBlock("for (int i = 0; i < values.length; i++) {", "}", () -> codec.emitEncode("values[i]"));
        });

        writer.write("@Override");
        writer.openBlock("public int size() {", "}", () -> {
            writer.write("""
                int size = this.size;
                if (size >= 0) {
                    return size;
                }
                Object[] values = this.values;
                size = 0;""");
            writer.openBlock("for (int i = 0; i < values.length; i++) {", "}", () -> codec.emitAddSize("values[i]"));
            writer.write("""
                this.size = size;
                return size;""");
        });

        writer.dedent().write("}");
    }

    private void generateMap(MapShape map) {
        Shape value = model.expectShape(map.getValue().getTarget());
        ElementCodec valueCodec = codecFor(value);
        ElementCodec codec = map.hasTrait(SparseTrait.class)
            ? new SparseElementCodec(valueCodec)
            : valueCodec;
        writer.putContext("valueType", symbolProvider.toSymbol(value));

        writer.write(
            "public final class ${className:L} extends $T<${valueType:T}> {",
            CommonSymbols.NestedCollectionMap
        );
        writer.indent();

        writer.write("@Override");
        writer.openBlock("public void fromMap(${userType:T} map) {", "}\n", () -> {
            writer.write("""
                int len = map.size();
                if (len == 0) {
                    setEmpty();
                    return;
                }
                ${byteBuffer:T}[] keys = new ${byteBuffer:T}[len];
                Object[] values = new Object[len];
                int size = 1 + 2 * $T($T(len));
                int i = 0;""", uintSize, encodeLenPrefixedListLength);
            writer.openBlock("for (${entry:T}<String, ${valueType:T}> e : map.entrySet()) {", "}", () -> {
                writer.write("""
                    byte[] key = e.getKey().getBytes(${uTF_8:T});
                    keys[i] = ${byteBuffer:T}.wrap(key);
                    size += $T(key.length);""", byteListLengthEncodedSize);
                codec.emitFromUser("e.getValue()", "values[i]");
                writer.write("i++;");
            });
            writer.write("init(keys, values, size);");
        });

        writer.write("@Override");
        writer.openBlock("public ${userType:T} toMap() {", "}\n", () -> {
            writer.write("""
                ${byteBuffer:T}[] keys = this.keys;
                Object[] values = this.values;
                ${userType:T} m = new ${hashMap:T}<>(keys.length / 3 * 4);""");
            writer.openBlock("for (int i = 0; i < keys.length; i++) {", "}", () -> {
                writer.write("m.put(string(keys[i]), $L);", codec.toUserExpr("values[i]"));
            });
            writer.write("return m;");
        });

        writer.write("@Override");
        writer.openBlock("protected Object[] readValues(${sparrowhawkDeserializer:T} d, int n) {", "}\n", () -> {
            writer.write("Object[] values = new Object[n];");
            writer.openBlock("for (int i = 0; i < n; i++) {", "}", () -> codec.emitDecode("values[i]"));
            writer.write("return values;");
        });

        writer.write("@Override");
        writer.openBlock("protected void writeValues(${sparrowhawkSerializer:T} s) {", "}\n", () -> {
            writer.write("""
                Object[] values = this.values;
                s.writeVarUL($T(values.length));""", encodeLenPrefixedListLength);
            writer.openBlock("for (int i = 0; i < values.length; i++) {", "}", () -> codec.emitEncode("values[i]"));
        });

        writer.write("@Override");
        writer.openBlock("protected int sizeofValues() {", "}\n", () -> {
            writer.write("""
                Object[] values = this.values;
                int size = ${ulongSize:T}($T(values.length));""", encodeLenPrefixedListLength);
            writer.openBlock("for (int i = 0; i < values.length; i++) {", "}", () -> codec.emitAddSize("values[i]"));
            writer.write("return size;");
        });

        writer.write("@Override");
        writer.openBlock("protected int decodeValueCount(int encodedCount) {", "}", () -> {
            writer.write("return $T(encodedCount);", decodeLenPrefixedListLengthChecked);
        });

        writer.dedent().write("}");
    }

    private void generateSet(ListShape set) {
        Shape member = model.expectShape(set.getMember().getTarget());
        writer.putContext("elementType", symbolProvider.toSymbol(member));

        SymbolReference headerEncoder = switch (member.getType()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM, LONG -> encodeVarintListLength;
            case FLOAT -> encodeFourBListLength;
            case DOUBLE, TIMESTAMP -> encodeEightBListLength;
            case STRING, ENUM, BLOB, BIG_INTEGER, BIG_DECIMAL -> encodeLenPrefixedListLength;
            default -> throw new IllegalStateException("rejected by validation: " + set);
        };
        SymbolReference countDecoder = switch (member.getType()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM, LONG -> CommonSymbols.decodeVarintListLengthChecked;
            case FLOAT -> CommonSymbols.decodeFourByteListLengthChecked;
            case DOUBLE, TIMESTAMP -> CommonSymbols.decodeEightByteListLengthChecked;
            case STRING, ENUM, BLOB, BIG_INTEGER, BIG_DECIMAL -> decodeLenPrefixedListLengthChecked;
            default -> throw new IllegalStateException("rejected by validation: " + set);
        };
        String writeStatement = switch (member.getType()) {
            case BOOLEAN -> "s.writeBool(e);";
            case BYTE -> "s.writeVarB(e);";
            case SHORT -> "s.writeVarS(e);";
            case INTEGER, INT_ENUM -> "s.writeVarI(e);";
            case LONG -> "s.writeVarL(e);";
            case FLOAT -> "s.writeFloat(e);";
            case DOUBLE -> "s.writeDouble(e);";
            case TIMESTAMP -> "s.writeDate(e);";
            case STRING, ENUM -> "s.writeString(e);";
            case BLOB -> "s.writeBytes(e);";
            case BIG_INTEGER -> "s.writeBigInteger(e);";
            case BIG_DECIMAL -> "new ${sparrowhawkBigDecimalHolder:T}(e).encodeTo(s);";
            default -> throw new IllegalStateException("rejected by validation: " + set);
        };
        String decodeExpression = switch (member.getType()) {
            case BOOLEAN -> "d.bool()";
            case BYTE -> "d.varB()";
            case SHORT -> "d.varS()";
            case INTEGER, INT_ENUM -> "d.varI()";
            case LONG -> "d.varL()";
            case FLOAT -> "d.f4()";
            case DOUBLE -> "d.d8()";
            case TIMESTAMP -> "d.date()";
            case STRING, ENUM -> "d.string()";
            case BLOB -> settings.zeroCopyBuffers() ? "d.bytes()" : "d.bytesCopied()";
            case BIG_INTEGER -> "d.bigInteger()";
            case BIG_DECIMAL -> "d.bigDecimal()";
            default -> throw new IllegalStateException("rejected by validation: " + set);
        };
        Runnable addElementSize = () -> {
            switch (member.getType()) {
                case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM -> writer.write("size += $T(e);", intSize);
                case LONG -> writer.write("size += $T(e);", longSize);
                case FLOAT -> writer.write("size += 4;");
                case DOUBLE, TIMESTAMP -> writer.write("size += 8;");
                case STRING, ENUM ->
                    writer.write("size += $T(e.getBytes(${uTF_8:T}).length);", byteListLengthEncodedSize);
                case BLOB -> writer.write("size += $T(e.remaining());", byteListLengthEncodedSize);
                case BIG_INTEGER -> writer.write("size += $T(e.toByteArray().length);", byteListLengthEncodedSize);
                case BIG_DECIMAL -> writer.write(
                    "size += $T(new ${sparrowhawkBigDecimalHolder:T}(e).size());",
                    byteListLengthEncodedSize
                );
                default -> throw new IllegalStateException("rejected by validation: " + set);
            }
        };

        writer.write("public final class ${className:L} implements ${sparrowhawkObject:T} {");
        writer.indent();
        writer.write("""
            private ${userType:T} values = new ${linkedHashSet:T}<>();
            private int size = -1;
            """);

        writer.openBlock("public static ${className:L} fromList(${userType:T} list) {", "}\n", () -> {
            writer.write("""
                ${className:L} l = new ${className:L}();
                int size = 0;""");
            writer.openBlock("for (${elementType:T} e : list) {", "}", addElementSize);
            writer.write("""
                l.values = list;
                l.size = size;
                return l;""");
        });

        writer.write("""
            public ${userType:T} toList() {
                return values;
            }

            public int elementCount() {
                return values.size();
            }
            """);

        writer.write("@Override");
        writer.openBlock("public void decodeFrom(${sparrowhawkDeserializer:T} d) {", "}\n", () -> {
            writer.write("""
                int count = $T(d.varUL());
                if (count <= 0) {
                    this.values = new ${linkedHashSet:T}<>();
                    this.size = 0;
                    return;
                }
                d.checkElementCount(count);
                ${userType:T} values = new ${linkedHashSet:T}<>(count / 3 * 4 + 1);
                int start = d.pos();""", countDecoder);
            writer.openBlock("for (int i = 0; i < count; i++) {", "}", () -> {
                writer.write("if (!values.add($L)) {", decodeExpression).indent();
                writer.write("throw new ${parseException:T}(\"duplicate element in @uniqueItems collection\");");
                writer.dedent().write("}");
            });
            writer.write("""
                this.values = values;
                this.size = d.pos() - start;""");
        });

        writer.write("@Override");
        writer.openBlock("public void encodeTo(${sparrowhawkSerializer:T} s) {", "}\n", () -> {
            writer.write("""
                ${userType:T} values = this.values;
                s.writeVarUL($T(values.size()));""", headerEncoder);
            writer.openBlock("for (${elementType:T} e : values) {", "}", () -> writer.write(writeStatement));
        });

        writer.write("@Override");
        writer.openBlock("public int size() {", "}", () -> {
            writer.write("""
                int size = this.size;
                if (size >= 0) {
                    return size;
                }
                size = 0;""");
            writer.openBlock("for (${elementType:T} e : values) {", "}", addElementSize);
            writer.write("""
                this.size = size;
                return size;""");
        });

        writer.dedent().write("}");
    }

    private abstract class ElementCodec {
        abstract void emitFromUser(String userVar, String target);

        abstract void emitAddSize(String stored);

        abstract void emitEncode(String stored);

        abstract void emitDecode(String target);

        abstract String toUserExpr(String stored);
    }

    private ElementCodec codecFor(Shape element) {
        if (element instanceof ListShape list) {
            if (CollectionSupport.needsFlyweight(model, list)) {
                return new ImplListCodec(generatedRef(list));
            }
            Shape member = model.expectShape(list.getMember().getTarget());
            Symbol listType = symbolProvider.toSymbol(list);
            if (list.hasTrait(SparseTrait.class)) {
                return switch (member.getType()) {
                    case STRUCTURE, UNION -> new SparseStructListCodec(listType, symbolProvider.toSymbol(member));
                    case BLOB -> new SparseBlobListCodec(listType);
                    default -> new ImplListCodec(sparseLeafImpl(member.getType()));
                };
            }
            return switch (member.getType()) {
                case STRING, ENUM -> new ImplListCodec(CommonSymbols.StringList);
                case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE, TIMESTAMP ->
                    new ScalarListCodec(listType, member.getType());
                case STRUCTURE, UNION -> new StructListCodec(listType, symbolProvider.toSymbol(member));
                case BLOB -> new BlobListCodec(listType);
                default -> throw new IllegalStateException("rejected by validation: " + list);
            };
        }
        if (element instanceof MapShape map) {
            if (CollectionSupport.needsFlyweight(model, map)) {
                return new MapImplCodec(generatedRef(map), null);
            }
            Shape value = model.expectShape(map.getValue().getTarget());
            if (isStructure(value)) {
                SymbolReference impl = map.hasTrait(SparseTrait.class)
                    ? CommonSymbols.SparseStructureMap
                    : CommonSymbols.StructureMap;
                return new MapImplCodec(impl, symbolProvider.toSymbol(value));
            }
            return new MapImplCodec(flatMapImpl(value.getType()), null);
        }
        return switch (element.getType()) {
            case BIG_INTEGER -> new BigIntegerCodec(symbolProvider.toSymbol(element));
            case BIG_DECIMAL -> new BigDecimalCodec();
            default -> throw new IllegalStateException("not a collection element: " + element);
        };
    }

    private SymbolReference generatedRef(Shape collection) {
        return symbolProvider.toSymbol(collection)
            .expectProperty("generatedCollection", Symbol.class)
            .toReference(null);
    }

    private SymbolReference sparseLeafImpl(ShapeType type) {
        return switch (type) {
            case STRING, ENUM -> CommonSymbols.SparseStringList;
            case BOOLEAN -> CommonSymbols.SparseBooleanList;
            case BYTE -> CommonSymbols.SparseByteList;
            case SHORT -> CommonSymbols.SparseShortList;
            case INTEGER, INT_ENUM -> CommonSymbols.SparseIntegerList;
            case LONG -> CommonSymbols.SparseLongList;
            case FLOAT -> CommonSymbols.SparseFloatList;
            case DOUBLE -> CommonSymbols.SparseDoubleList;
            case TIMESTAMP -> CommonSymbols.SparseTimestampList;
            default -> throw new IllegalStateException("rejected by validation: " + type);
        };
    }

    private SymbolReference flatMapImpl(ShapeType type) {
        return switch (type) {
            case BOOLEAN -> CommonSymbols.BooleanMap;
            case BYTE -> CommonSymbols.ByteMap;
            case SHORT -> CommonSymbols.ShortMap;
            case INTEGER, INT_ENUM -> CommonSymbols.IntegerMap;
            case LONG -> CommonSymbols.LongMap;
            case FLOAT -> CommonSymbols.FloatMap;
            case DOUBLE -> CommonSymbols.DoubleMap;
            case TIMESTAMP -> CommonSymbols.TimestampMap;
            case STRING, ENUM -> CommonSymbols.StringMap;
            case BLOB -> settings.zeroCopyBuffers() ? CommonSymbols.BytesMap : CommonSymbols.CopiedBytesMap;
            default -> throw new IllegalStateException("rejected by validation: " + type);
        };
    }

    private static String boxedListName(ShapeType type) {
        return switch (type) {
            case BOOLEAN -> "Boolean";
            case BYTE -> "Byte";
            case SHORT -> "Short";
            case INTEGER, INT_ENUM -> "Integer";
            case LONG -> "Long";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case TIMESTAMP -> "Date";
            default -> throw new IllegalStateException("not a scalar list member: " + type);
        };
    }

    private final class SparseElementCodec extends ElementCodec {
        private final ElementCodec inner;

        SparseElementCodec(ElementCodec inner) {
            this.inner = inner;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("if ($L == null) {", userVar).indent();
            writer.write("size += 1;");
            writer.write("$L = null;", target);
            writer.dedent().write("} else {").indent();
            writer.write("int _wrapOuter = size;");
            writer.write("size = 0;");
            inner.emitFromUser(userVar, target);
            writer.write("size = _wrapOuter + $T(1 + size);", byteListLengthEncodedSize);
            writer.dedent().write("}");
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("if ($L == null) {", stored).indent();
            writer.write("size += 1;");
            writer.dedent().write("} else {").indent();
            writer.write("int _wrapOuter = size;");
            writer.write("size = 0;");
            inner.emitAddSize(stored);
            writer.write("size = _wrapOuter + $T(1 + size);", byteListLengthEncodedSize);
            writer.dedent().write("}");
        }

        @Override
        void emitEncode(String stored) {
            writer.write("if ($L == null) {", stored).indent();
            writer.write("s.writeEmptyObject();");
            writer.dedent().write("} else {").indent();
            writer.write("int _wrapSize = 0;");
            writer.write("{").indent();
            writer.write("int size = 0;");
            inner.emitAddSize(stored);
            writer.write("_wrapSize = size;");
            writer.dedent().write("}");
            writer.write("s.writeVarUL($T(1 + _wrapSize));", CommonSymbols.encodeByteListLength);
            writer.write("s.writeExactlyOneListField();");
            writer.write("{").indent();
            inner.emitEncode(stored);
            writer.dedent().write("}");
            writer.dedent().write("}");
        }

        @Override
        void emitDecode(String target) {
            writer.write("if (d.varUL() == 0) {").indent();
            writer.write("$L = null;", target);
            writer.dedent().write("} else {").indent();
            writer.write("d.expectExactlyOneListField();");
            inner.emitDecode(target);
            writer.dedent().write("}");
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($L == null ? null : $L)", stored, inner.toUserExpr(stored));
        }
    }

    private final class ScalarListCodec extends ElementCodec {
        private final Symbol listType;
        private final ShapeType memberType;

        ScalarListCodec(Symbol listType, ShapeType memberType) {
            this.listType = listType;
            this.memberType = memberType;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $L;", listType, userVar);
            emitFootprint();
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("$T _v = ($T) $L;", listType, listType, stored);
            emitFootprint();
        }

        private void emitFootprint() {
            switch (memberType) {
                case FLOAT -> writer.write(
                    "size += $T($T(_v.size())) + 4 * _v.size();",
                    uintSize,
                    encodeFourBListLength
                );
                case DOUBLE, TIMESTAMP -> writer.write(
                    "size += $T($T(_v.size())) + 8 * _v.size();",
                    uintSize,
                    encodeEightBListLength
                );
                default -> {
                    writer.write("int _es = $T($T(_v.size()));", uintSize, encodeVarintListLength);
                    writer.openBlock("for (int _j = 0; _j < _v.size(); _j++) {", "}", () -> {
                        writer.write("_es += $T(_v.get(_j));", memberType == ShapeType.LONG ? longSize : intSize);
                    });
                    writer.write("size += _es;");
                }
            }
        }

        @Override
        void emitEncode(String stored) {
            writer.write("s.write$LList(($T) $L);", boxedListName(memberType), listType, stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("$L = d.decode$LList();", target, boxedListName(memberType));
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($T) $L", listType, stored);
        }
    }

    private final class BigIntegerCodec extends ElementCodec {
        private final Symbol type;

        BigIntegerCodec(Symbol type) {
            this.type = type;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $L;", type, userVar);
            writer.write("size += $T(_v.toByteArray().length);", byteListLengthEncodedSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("size += $T((($T) $L).toByteArray().length);", byteListLengthEncodedSize, type, stored);
        }

        @Override
        void emitEncode(String stored) {
            writer.write("s.writeBigInteger($L);", stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("$L = d.bigInteger();", target);
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($T) $L", type, stored);
        }
    }

    private final class BigDecimalCodec extends ElementCodec {
        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("${sparrowhawkBigDecimalHolder:T} _v = new ${sparrowhawkBigDecimalHolder:T}($L);", userVar);
            writer.write("size += $T(_v.size());", byteListLengthEncodedSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write(
                "size += $T(((${sparrowhawkBigDecimalHolder:T}) $L).size());",
                byteListLengthEncodedSize,
                stored
            );
        }

        @Override
        void emitEncode(String stored) {
            writer.write("((${sparrowhawkBigDecimalHolder:T}) $L).encodeTo(s);", stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("${sparrowhawkBigDecimalHolder:T} _v = new ${sparrowhawkBigDecimalHolder:T}();");
            writer.write("_v.decodeFrom(d);");
            writer.write("$L = _v;", target);
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("(($T) $L).toBigDecimal()", CommonSymbols.SparrowhawkBigDecimalHolder, stored);
        }
    }

    private final class ImplListCodec extends ElementCodec {
        private final SymbolReference impl;

        ImplListCodec(SymbolReference impl) {
            this.impl = impl;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $T.fromList($L);", impl, impl, userVar);
            writer.write("size += $T(_v.size(), _v.elementCount());", lenPrefixedListLengthEncodedSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("$T _v = ($T) $L;", impl, impl, stored);
            writer.write("size += $T(_v.size(), _v.elementCount());", lenPrefixedListLengthEncodedSize);
        }

        @Override
        void emitEncode(String stored) {
            writer.write("(($T) $L).encodeTo(s);", impl, stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("$T _v = new $T();", impl, impl);
            writer.write("_v.decodeFrom(d);");
            writer.write("$L = _v;", target);
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("(($T) $L).toList()", impl, stored);
        }
    }

    private final class MapImplCodec extends ElementCodec {
        private final SymbolReference impl;
        private final Symbol structValue;

        MapImplCodec(SymbolReference impl, Symbol structValue) {
            this.impl = impl;
            this.structValue = structValue;
        }

        private void emitNew() {
            if (structValue == null) {
                writer.write("$T _v = new $T();", impl, impl);
            } else {
                writer.write("$T<$T> _v = new $T<>($T::new);", impl, structValue, impl, structValue);
            }
        }

        @Override
        void emitFromUser(String userVar, String target) {
            emitNew();
            writer.write("_v.fromMap($L);", userVar);
            writer.write("size += $T(_v.size());", byteListLengthEncodedSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("size += $T((($T) $L).size());", byteListLengthEncodedSize, impl, stored);
        }

        @Override
        void emitEncode(String stored) {
            writer.write("(($T) $L).encodeTo(s);", impl, stored);
        }

        @Override
        void emitDecode(String target) {
            emitNew();
            writer.write("_v.decodeFrom(d);");
            writer.write("$L = _v;", target);
        }

        @Override
        String toUserExpr(String stored) {
            if (structValue == null) {
                return writer.format("(($T) $L).toMap()", impl, stored);
            }
            return writer.format("(($T<$T>) $L).toMap()", impl, structValue, stored);
        }
    }

    private final class StructListCodec extends ElementCodec {
        private final Symbol listType;
        private final Symbol struct;

        StructListCodec(Symbol listType, Symbol struct) {
            this.listType = listType;
            this.struct = struct;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $L;", listType, userVar);
            emitFootprint();
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("$T _v = ($T) $L;", listType, listType, stored);
            emitFootprint();
        }

        private void emitFootprint() {
            writer.write("int _es = $T($T(_v.size()));", uintSize, encodeLenPrefixedListLength);
            writer.openBlock("for (int _j = 0; _j < _v.size(); _j++) {", "}", () -> {
                writer.write("_es += $T(_v.get(_j).size());", byteListLengthEncodedSize);
            });
            writer.write("size += _es;");
        }

        @Override
        void emitEncode(String stored) {
            writer.write("$T _v = ($T) $L;", listType, listType, stored);
            writer.write("s.writeVarUL($T(_v.size()));", encodeLenPrefixedListLength);
            writer.openBlock("for (int _j = 0; _j < _v.size(); _j++) {", "}", () -> {
                writer.write("_v.get(_j).encodeTo(s);");
            });
        }

        @Override
        void emitDecode(String target) {
            writer.write("int _n = $T(d.varUL());", decodeLenPrefixedListLengthChecked);
            writer.write("d.checkElementCount(_n);");
            writer.write("$T _v = new ${arrayList:T}<>(_n);", listType);
            writer.openBlock("for (int _j = 0; _j < _n; _j++) {", "}", () -> {
                writer.write("$T _x = new $T();", struct, struct);
                writer.write("_x.decodeFrom(d);");
                writer.write("_v.add(_x);");
            });
            writer.write("$L = _v;", target);
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($T) $L", listType, stored);
        }
    }

    private final class BlobListCodec extends ElementCodec {
        private final Symbol listType;

        BlobListCodec(Symbol listType) {
            this.listType = listType;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $L;", listType, userVar);
            writer.write("size += $T(_v);", blobListEncodedSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("size += $T(($T) $L);", blobListEncodedSize, listType, stored);
        }

        @Override
        void emitEncode(String stored) {
            writer.write("s.writeBlobList(($T) $L);", listType, stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("$L = d.decode$LByteBufferList();", target, settings.zeroCopyBuffers() ? "" : "Copied");
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($T) $L", listType, stored);
        }
    }

    private final class SparseStructListCodec extends ElementCodec {
        private final Symbol listType;
        private final Symbol struct;

        SparseStructListCodec(Symbol listType, Symbol struct) {
            this.listType = listType;
            this.struct = struct;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $L;", listType, userVar);
            writer.write("size += $T(_v);", sparseObjectListSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("size += $T(($T) $L);", sparseObjectListSize, listType, stored);
        }

        @Override
        void emitEncode(String stored) {
            writer.write("s.writeSparseObjectList(($T) $L);", listType, stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("$L = d.decodeSparseObjectList($T::new);", target, struct);
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($T) $L", listType, stored);
        }
    }

    private final class SparseBlobListCodec extends ElementCodec {
        private final Symbol listType;

        SparseBlobListCodec(Symbol listType) {
            this.listType = listType;
        }

        @Override
        void emitFromUser(String userVar, String target) {
            writer.write("$T _v = $L;", listType, userVar);
            writer.write("size += $T(_v);", sparseBlobListSize);
            writer.write("$L = _v;", target);
        }

        @Override
        void emitAddSize(String stored) {
            writer.write("size += $T(($T) $L);", sparseBlobListSize, listType, stored);
        }

        @Override
        void emitEncode(String stored) {
            writer.write("s.writeSparseBlobList(($T) $L);", listType, stored);
        }

        @Override
        void emitDecode(String target) {
            writer.write("$L = d.decode$LSparseBlobList();", target, settings.zeroCopyBuffers() ? "" : "Copied");
        }

        @Override
        String toUserExpr(String stored) {
            return writer.format("($T) $L", listType, stored);
        }
    }
}

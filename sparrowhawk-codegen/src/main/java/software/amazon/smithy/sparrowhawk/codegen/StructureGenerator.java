/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.Objects;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.SparseStringList;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.StringList;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_EIGHT;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_FOUR;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_LIST;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_VARINT;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.asList;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.byteListLengthEncodedSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.decodeElementCount;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeByteListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeEightBListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeFourBListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeLenPrefixedListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeVarintListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.intSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.lenPrefixedListLengthEncodedSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.longSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.missingField;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.uintSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.ulongSize;
import static software.amazon.smithy.sparrowhawk.codegen.Util.isStructure;
import static software.amazon.smithy.utils.StringUtils.capitalize;
import static software.amazon.smithy.utils.StringUtils.upperCase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.java.sparrowhawk.KConstants;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.sparrowhawk.codegen.CodeSections.EndClassSection;
import software.amazon.smithy.sparrowhawk.codegen.CodeSections.StartClassSection;

public final class StructureGenerator implements Runnable {
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final SparrowhawkSettings settings;
    private final Symbol symbol;
    private final Model model;
    private final SparrowhawkIndex index;
    private final List<FieldSet> fieldSets = new ArrayList<>();
    private final JavaWriter writer;

    StructureGenerator(
        Shape shape,
        Model model,
        SymbolProvider symbolProvider,
        JavaWriter writer,
        SparrowhawkSettings settings
    ) {
        this.shape = shape;
        this.model = model;
        this.symbolProvider = symbolProvider;
        this.settings = settings;
        this.symbol = symbolProvider.toSymbol(shape);
        this.writer = writer;
        this.index = SparrowhawkIndex.of(model);
    }

    public Shape getShape() {
        return shape;
    }

    public SymbolProvider getSymbolProvider() {
        return symbolProvider;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public Model getModel() {
        return model;
    }

    public SparrowhawkIndex getIndex() {
        return index;
    }

    public JavaWriter getWriter() {
        return writer;
    }

    public void run() {
        writer.pushState(new StartClassSection(this));
        writer.write("public final class $L implements $T {", symbol.getName(), CommonSymbols.SparrowhawkObject);
        writer.popState();
        writer.indent();
        for (int i = 0; i < index.getVarintFieldSetCount(shape); i++) {
            generateFields(index.getVarintMembers(shape, i), i);
        }
        for (int i = 0; i < index.getListFieldSetCount(shape); i++) {
            generateFields(index.getListMembers(shape, i), i);
        }
        for (int i = 0; i < index.getFourByteFieldSetCount(shape); i++) {
            generateFields(index.getFourByteMembers(shape, i), i);
        }
        for (int i = 0; i < index.getEightByteFieldSetCount(shape); i++) {
            generateFields(index.getEightByteMembers(shape, i), i);
        }
        generateSizingMethods();
        generateEncoder();
        generateDecoder();
        generateEquals();
        writer.injectSection(new EndClassSection(this));
        writer.dedent().write("}");
    }

    private void generateFields(List<MemberShape> fields, int secIdx) {
        if (fields.isEmpty()) {
            return;
        }

        // shift over 3 for type bits
        var fieldType = fields.get(0).expectTrait(SparrowhawkFieldTrait.class).getType();
        var required = 0L;
        for (int i = 0; i < fields.size(); i++) {
            if (isRequired(fields.get(i))) {
                required |= (1L << i);
            }
        }

        required = (required << 3) | fieldType.wireType;
        if (secIdx > 0) {
            required = required | 0b100;
        }

        writer.write(
            "private static final long REQUIRED_$L_$L = $L;",
            fieldType.uppercaseId,
            secIdx,
            bitsToString(required)
        );
        var fieldsetName = "$" + fieldType.lowercaseId + "_" + secIdx;
        fieldSets.add(new FieldSet(fieldsetName, fieldType, secIdx));
        writer.write("private long $L = REQUIRED_$L_$L;", fieldsetName, fieldType.uppercaseId, secIdx);

        for (MemberShape field : fields) {
            generateField(field, fieldsetName);
        }
    }

    private JavaWriter generateMethod(String prelude, Runnable generator) {
        return writer.openBlock("$L {", "}\n", prelude, generator);
    }

    private JavaWriter generateMethod(String prelude, Object arg1, Runnable generator) {
        return writer.openBlock("$L {", "}\n", writer.format(prelude, arg1), generator);
    }

    private void generateField(MemberShape field, String fieldsetName) {
        // TODO: default values
        var fieldSymbol = symbolProvider.toSymbol(field);
        var fieldName = fieldName(field);
        var methodName = fieldSymbol.expectProperty("methodName", String.class);
        var toggleFieldName = "FIELD_" + upperCase(field.getId().getMember().get());
        var trait = field.expectTrait(SparrowhawkFieldTrait.class);

        var bit = 1L << ((trait.getTypeIdx() - 1) + 3);
        writer.write(
            "// $L fieldSet $L index $L",
            trait.getType().lowercaseId,
            trait.getFieldSetIdx(),
            trait.getTypeIdx()
        );
        writer.write("private static final long $L = $L;", toggleFieldName, bitsToString(bit));
        writer.write(
            "private $T $L;",
            fieldSymbol.getProperty("sparrowhawkField", Symbol.class)
                .orElse(fieldSymbol),
            fieldName
        );

        writer.openBlock("\npublic $T get$L() {", "}\n", fieldSymbol, methodName, () -> {
            var target = model.expectShape(field.getTarget());
            if (isString(target)) {
                writer.openBlock("if ($L == null) {", "}", fieldName, () -> {
                    writer.write("return null;");
                });
                writer.openBlock("if ($L instanceof String) {", "}", fieldName, () -> {
                    writer.write("return (String) $L;", fieldName);
                });
                writer.write("""
                    String s = new String((byte[]) $1L, $2T);
                    this.$1L = s;
                    return s;""", fieldName, CommonSymbols.UTF_8);
            } else if (target.isMapShape()) {
                var sparrowhawkCollectionSymbol = fieldSymbol.expectProperty(
                    "sparrowhawkCollection",
                    SymbolReference.class
                );
                writer.write("""
                    Object field = $L;
                    if (field == null) return null;""", fieldName);
                writer.openBlock("if (field.getClass() == $T.class) {", "}", sparrowhawkCollectionSymbol, () -> {
                    writer.write("""
                        $T m = (($T) field).toMap();
                        this.$L = m;
                        return m;""", fieldSymbol, sparrowhawkCollectionSymbol, fieldName);
                });
                writer.write("return ($T) $L;", fieldSymbol, fieldName);
            } else if (target.isListShape()) {
                var valueSymbol = listTarget(model.expectShape(field.getTarget()));
                var valueType = valueSymbol.expectProperty("shape", Shape.class);
                if (isString(valueType)) {
                    var implType = isSparse(field) ? SparseStringList : StringList;
                    writer.write("""
                        Object field = $L;
                        if (field == null) return null;""", fieldName);
                    writer.openBlock("if (field.getClass() == $T.class) {", "}", implType, () -> {
                        writer.write("""
                            List<String> m = (($T) field).toList();
                            this.$L = m;
                            return m;""", implType, fieldName);
                    });
                    writer.write("return (List<String>) $L;", fieldName);
                } else {
                    writer.write("return $L;", fieldName);
                }
            } else {
                writer.write("return $L;", fieldName);
            }
        });

        writer.openBlock("public void set$L($T $L) {", "}\n", methodName, fieldSymbol, fieldName, () -> {
            if (trait.getType() == FieldType.LIST) {
                if (isRequired(field)) {
                    writer.openBlock("if ($L == null) {", "}", fieldName, () -> {
                        writer.write("""
                            $2T("'$1L' is required");""", field.getMemberName(), missingField);
                    });
                } else {
                    writer.write("if ($L == null) {", fieldName)
                        .indent()
                        .write("$L &= ~$L;", fieldsetName, toggleFieldName)
                        .dedent();
                    writer.write("} else {")
                        .indent()
                        .write("$L |= $L;", fieldsetName, toggleFieldName)
                        .dedent();
                    writer.write("}");
                }
            } else if (isOptional(field)) {
                writer.write("$L |= $L;", fieldsetName, toggleFieldName);
            }
            writer.write("this.$L = $L;", fieldName, fieldName);
            writer.write("this.$$size = -1;");
        });

        writer.openBlock("public boolean has$L() {", "}\n", methodName, () -> {
            writer.write("return ($L & $L) != 0;", fieldsetName, toggleFieldName);
        });
    }

    private void generateSizingMethods() {
        writer.write("private int $$size = -1;");
        generateMethod("public int size()", this::generateSizeMethod);
        if (index.hasVarintMembers(shape)) {
            generateMethod("private int sizeVarints()", this::generateVarintSizeMethods);
        }
        if (index.hasFourByteMembers(shape)) {
            generateMethod("private int sizeFourByteFields()", this::generateFourByteSizeMethods);
        }
        if (index.hasEightByteMembers(shape)) {
            generateMethod("private int sizeEightByteFields()", this::generateEightByteSizeMethods);
        }
        if (index.hasListMembers(shape)) {
            generateListSizeMethods();
        }
    }

    private void generateSizeMethod() {
        if (fieldSets.isEmpty()) {
            writer.write("return 0;");
            return;
        }

        writer.openBlock("if ($$size >= 0) {", "}\n", () -> writer.write("return $$size;"));
        // TODO: use a constant when a fieldset has a guaranteed serialized size
        writer.writeInline("int size = ");
        boolean add = false;
        for (var fieldSet : fieldSets) {
            if (add) {
                writer.writeInline(" + ");
            } else {
                add = true;
            }
            writer.writeInline(
                "($1L == $2L ? 0 : ($3T($1L)",
                fieldSet.name(),
                bitsToString(getEmpty(fieldSet.type().wireType, fieldSet.fieldSetIdx())),
                ulongSize
            );
            if (fieldSet.fieldSetIdx() > 0) {
                writer.writeInline(" + $L", SparrowhawkSerializer.uintSize(fieldSet.fieldSetIdx() - 1));
            }
            writer.writeInline("))");
        }
        writer.write(";");
        if (index.hasVarintMembers(shape)) {
            writer.write("size += sizeVarints();");
        }
        if (index.hasListMembers(shape)) {
            writer.write("size += sizeListFields();");
        }
        if (index.hasFourByteMembers(shape)) {
            writer.write("size += sizeFourByteFields();");
        }
        if (index.hasEightByteMembers(shape)) {
            writer.write("size += sizeEightByteFields();");
        }
        writer.write("""
            this.$$size = size;
            return size;""");
    }

    public String methodNameForField(MemberShape field) {
        return symbolProvider.toSymbol(field).expectProperty("methodName", String.class);
    }

    public String fieldName(MemberShape field) {
        return symbolProvider.toMemberName(field);
    }

    private void variableSizeSizer(Stream<MemberShape> fields, Consumer<MemberShape> sizer) {
        writer.write("int size = 0;");
        fields.forEach(field -> {
            if (isOptional(field)) {
                writer.openBlock("if (has$L()) {", methodNameForField(field));
            }
            sizer.accept(field);
            if (isOptional(field)) {
                writer.closeBlock("}");
            }
        });
        writer.write("return size;");
    }

    private void generateVarintSizeMethods() {
        variableSizeSizer(getAllVarintMembers(), (field) -> {
            var method = model.expectShape(field.getTarget()).isLongShape() ? longSize : intSize;
            writer.write("size += $T($L);", method, fieldName(field));
        });
    }

    private void generateListSizeMethods() {
        List<Sizer> extraSizers = new ArrayList<>();
        writer.openBlock("private int sizeListFields() {", "}\n", () -> {
            writer.write("int size = 0;");
            for (int i = 0; i < index.getListFieldSetCount(shape); i++) {
                for (var field : index.getListMembers(shape, i)) {
                    if (isOptional(field)) {
                        writer.openBlock("if (has$L()) {", methodNameForField(field));
                    }

                    var shape = model.expectShape(field.getTarget());
                    var fieldName = fieldName(field);
                    if (shape.isBlobShape()) {
                        writer.write("size += $T($L.remaining());", byteListLengthEncodedSize, fieldName);
                    } else if (isStructure(shape)) {
                        writer.write("size += $T($L.size());", byteListLengthEncodedSize, fieldName);
                    } else {
                        Sizer sizer = null;
                        if (shape.isListShape()) {
                            var listType = model.expectShape(
                                ((ListShape) model.expectShape(field.getTarget())).getMember().getTarget()
                            );
                            switch (listType.getType()) {
                                case STRING, ENUM -> sizer = new StringListSizer(field);
                                case BYTE, SHORT, INTEGER, LONG, BOOLEAN, INT_ENUM -> sizer = new VarIntListSizer(
                                    field,
                                    listType.getType()
                                );
                                case FLOAT -> writer.write(
                                    "size += ($1L.size() * 4) + $2T($3T($1L.size()));",
                                    fieldName,
                                    ulongSize,
                                    encodeFourBListLength
                                );
                                case DOUBLE -> writer.write(
                                    "size += ($1L.size() * 8) + $2T($3T($1L.size()));",
                                    fieldName,
                                    ulongSize,
                                    encodeEightBListLength
                                );
                                case STRUCTURE -> sizer = new StructureListSizer(field);
                                default -> throw new IllegalStateException("Unexpected value: " + listType.getType());
                            }
                        } else if (isString(shape)) {
                            sizer = new StringSizer(field);
                        } else if (shape.isMapShape()) {
                            sizer = new MapSizer(field);
                        } else {
                            throw new RuntimeException("Bad list type: " + shape.getType());
                        }

                        if (sizer != null) {
                            extraSizers.add(sizer);
                            writer.write("size += $L();", sizer.methodName());
                        }
                    }

                    if (isOptional(field)) {
                        writer.closeBlock("}");
                    }
                }
            }

            writer.write("return size;");
        });

        for (var sizer : extraSizers) {
            sizer.generate();
        }
    }

    private abstract class Sizer {
        protected final MemberShape field;

        private Sizer(MemberShape field) {
            this.field = field;
        }

        String methodName() {
            return "$" + field.getMemberName() + "Len";
        }

        abstract void generate();
    }

    private final class MapSizer extends Sizer {
        private MapSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate() {
            writer.openBlock("private int $L() {", "}\n", methodName(), () -> {
                var fieldName = fieldName(field);
                writer.write("Object field = $L;", fieldName);
                if (isRequired(field)) {
                    writer.openBlock("if (field == null) {", "}", () -> {
                        writer.write(
                            "$2T(\"Required field '$1L' is missing\");",
                            field.getMemberName(),
                            missingField
                        );
                    });
                }

                var mapSymbol = symbolProvider.toSymbol(field);
                var sparrowhawkCollectionSymbol = mapSymbol.expectProperty(
                    "sparrowhawkCollection",
                    SymbolReference.class
                );
                var valueSymbol = mapValueTarget(model.expectShape(field.getTarget()));
                writer.write("int size;");
                writer.write("if (field.getClass() == $T.class) {", sparrowhawkCollectionSymbol);
                writer.indent().write("size = (($T) field).size();", sparrowhawkCollectionSymbol);
                writer.dedent().write("} else {");
                writer.indent()
                    .write(
                        """
                            $1T m = new $1T($2C);
                            m.fromMap(($3T) field);
                            this.$4L = m;
                            size = m.size();""",
                        sparrowhawkCollectionSymbol,
                        writer.consumer(w -> {
                            if (isStructure(valueSymbol.expectProperty("shape", Shape.class))) {
                                w.writeInline("$T::new", valueSymbol);
                            }
                        }),
                        mapSymbol,
                        fieldName
                    );
                writer.dedent().write("}");
                writer.write("return $T(size);", byteListLengthEncodedSize);
            });
        }
    }

    private static boolean isString(Shape shape) {
        return shape.isStringShape() || shape.isEnumShape();
    }

    private final class StringListSizer extends Sizer {
        StringListSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate() {
            writer.openBlock("private int $L() {", "}\n", methodName(), () -> {
                var listType = isSparse(field) ? SparseStringList : StringList;
                var fieldName = fieldName(field);
                writer.write("Object field = $L;", fieldName);
                if (isRequired(field)) {
                    writer.openBlock("if (field == null) {", "}\n", () -> {
                        writer.write(
                            "$2T(\"Required field '$1L' is missing\");",
                            field.getMemberName(),
                            missingField
                        );
                    });
                }
                writer.write("""
                    $1T _list;
                    if (field.getClass() == $1T.class) {""", listType);
                writer.indent()
                    .write("_list = ($T) field;", listType);
                writer.dedent().write("} else {");
                writer.indent().write("""
                    _list = $1T.fromList((List<String>) field);
                    this.$2L = _list;""", listType, fieldName);
                writer.dedent().write("}");
                writer.write("return $T(_list.size(), _list.elementCount());", lenPrefixedListLengthEncodedSize);
            });
        }
    }

    private final class StringSizer extends Sizer {
        StringSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate() {
            writer.openBlock("private int $L() {", "}\n", methodName(), () -> {
                var fieldName = fieldName(field);
                writer.write("Object field = $L;", fieldName);
                if (isRequired(field)) {
                    writer.openBlock("if (field == null) {", "}\n", () -> {
                        writer.write(
                            "$2T(\"Required field '$1L' is missing\");",
                            field.getMemberName(),
                            missingField
                        );
                    });
                }

                writer.write("int size;");
                // TODO: is this faster than instanceof?
                writer.write("if (field.getClass() == byte[].class) {");
                writer.indent().write("size = ((byte[]) field).length;");
                writer.dedent().write("} else {");
                writer.indent().write("""
                    byte[] bytes = ((String) field).getBytes($T);
                    this.$L = bytes;
                    size = bytes.length;""", CommonSymbols.UTF_8, fieldName);
                writer.dedent().write("}\n");
                writer.write("return $T(size);", byteListLengthEncodedSize);
            });
        }
    }

    private final class VarIntListSizer extends Sizer {

        private final ShapeType innerType;

        private VarIntListSizer(MemberShape field, ShapeType innerType) {
            super(field);
            this.innerType = innerType;
        }

        @Override
        void generate() {
            writer.openBlock("private int $L() {", "}\n", methodName(), () -> {
                var fieldName = fieldName(field);
                if (isRequired(field)) {
                    writer.openBlock("if ($L == null) {", "}\n", fieldName, () -> {
                        writer.write(
                            "$2T(\"Required field '$1L' is missing\");",
                            field.getMemberName(),
                            missingField
                        );
                    });
                }

                SymbolReference sizingMethod = innerType == LONG ? longSize : intSize;
                writer.write("int size = 0;");
                writer.write("int len = $L.size();", fieldName);
                writer.write("for (int i = 0; i < len; i++) {");
                writer.indent().write("size += $T($L.get(i));", sizingMethod, fieldName);
                writer.dedent().write("}");
                writer.write("size += $T($T(len));", uintSize, encodeVarintListLength);
                writer.write("return size;");
            });
        }
    }

    private final class StructureListSizer extends Sizer {
        StructureListSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate() {
            writer.openBlock("private int $L() {", "}\n", methodName(), () -> {
                var fieldName = fieldName(field);
                if (isRequired(field)) {
                    writer.openBlock("if ($L == null) {", "}\n", fieldName, () -> {
                        writer.write(
                            "$T(\"Required field '$L' is missing\");",
                            missingField,
                            field.getMemberName()
                        );
                    });
                }

                writer.write("int size = 0;");
                writer.write("int len = $L.size();", fieldName);
                writer.write("for (int i = 0; i < len; i++) {");
                writer.indent().write("size += $T($L.get(i).size());", byteListLengthEncodedSize, fieldName);
                writer.dedent().write("}");
                writer.write("size += $T($T(len));", uintSize, encodeLenPrefixedListLength);
                writer.write("return size;");
            });
        }
    }

    public Symbol mapValueTarget(Shape map) {
        return symbolProvider.toSymbol(map).expectProperty("value", Symbol.class);
    }

    public Symbol listTarget(Shape shape) {
        return symbolProvider.toSymbol(shape).expectProperty("value", Symbol.class);
    }

    private void fixedSizeSizer(Stream<MemberShape> fields, int scale) {
        var optionalFields = new ArrayList<MemberShape>();
        var requiredFields = new ArrayList<MemberShape>();
        fields.forEach(f -> {
            if (isOptional(f)) {
                optionalFields.add(f);
            } else {
                requiredFields.add(f);
            }
        });
        int requiredBytes = scale * requiredFields.size();
        writer.write("int size = $L;", requiredBytes);
        for (var field : optionalFields) {
            writer.openBlock("if (has$L()) {", "}", methodNameForField(field), () -> {
                writer.write("size += $L;", scale);
            });
        }
        writer.write("return size;");
    }

    private void generateFourByteSizeMethods() {
        fixedSizeSizer(getAllFourByteMembers(), 4);
    }

    private void generateEightByteSizeMethods() {
        fixedSizeSizer(getAllEightByteMembers(), 8);
    }

    private void generateEncoder() {
        generateMethod("public void encodeTo($T s)", CommonSymbols.SparrowhawkSerializer, this::generateEncodeTo);
        if (index.hasVarintMembers(shape)) {
            emitWriteVarints();
        }

        if (index.hasFourByteMembers(shape)) {
            emitFixedWidthEncoder(
                KConstants.T_FOUR,
                "fourByte",
                "Float",
                index::getFourByteFieldSetCount,
                index::getFourByteMembers
            );
        }

        if (index.hasEightByteMembers(shape)) {
            emitFixedWidthEncoder(
                KConstants.T_EIGHT,
                "eightByte",
                "Double",
                index::getEightByteFieldSetCount,
                index::getEightByteMembers
            );
        }

        if (index.hasListMembers(shape)) {
            emitListEncoder();
        }
    }

    private void emitListEncoder() {
        writer.openBlock("private void writeListFields($T s) {", "}\n", CommonSymbols.SparrowhawkSerializer, () -> {
            for (int j = 0; j < index.getListFieldSetCount(shape); j++) {
                int fieldSetIdx = j;
                writer.openBlock(
                    "if ($$list_$L != $L) {",
                    "}",
                    fieldSetIdx,
                    bitsToString(getEmpty(KConstants.T_LIST, fieldSetIdx)),
                    () -> {
                        writer.write("s.writeVarUL($$list_$L);", fieldSetIdx);
                        if (fieldSetIdx > 0) {
                            writer.write("s.writeVarUI($L);", fieldSetIdx - 1);
                        }
                        var listFields = index.getListMembers(shape, fieldSetIdx);
                        for (int i = 0; i < listFields.size(); i++) {
                            var field = listFields.get(i);
                            var fieldName = fieldName(field);
                            if (isOptional(field)) {
                                writer.openBlock("if (has$L()) {", methodNameForField(field));
                            }

                            var fieldSymbol = symbolProvider.toSymbol(field);
                            var target = model.expectShape(field.getTarget());
                            if (isString(target)) {
                                writer.write("s.writeBytes($L);", fieldName);
                            } else if (target.isBlobShape()) {
                                writer.write("s.writeBytes($L);", fieldName);
                            } else if (target.isMapShape()) {
                                writer.write(
                                    "(($T) $L).encodeTo(s);",
                                    fieldSymbol.expectProperty("sparrowhawkCollection", SymbolReference.class),
                                    fieldName
                                );
                            } else if (target.isListShape()) {
                                var valueType = listTarget(target);
                                var valueShape = valueType.expectProperty("shape", Shape.class);
                                if (isString(valueShape)) {
                                    var listType = isSparse(field) ? SparseStringList : StringList;
                                    writer.write("(($T) $L).encodeTo(s);", listType, fieldName);
                                } else if (isVarintShape(valueShape) || valueShape.isDoubleShape() || valueShape
                                    .isFloatShape()) {
                                        writer.write("s.write$TList($L);", valueType, fieldName);
                                    } else if (isStructure(valueShape)) {
                                        writer.write(
                                            "s.writeVarUL(encodeLenPrefixedListLength($L.size()));",
                                            fieldName
                                        );
                                        writer.write("for(int i = 0; i < $L.size(); i++) {", fieldName);
                                        writer.indent().write("$L.get(i).encodeTo(s);", fieldName);
                                        writer.dedent().write("}");
                                    } else {
                                        throw new RuntimeException("no list encoder for: " + field);
                                    }
                            } else if (isStructure(target)) {
                                writer.write("$L.encodeTo(s);", fieldName);
                            } else {
                                throw new RuntimeException("unsupported list encoder: " + field);
                            }

                            if (isOptional(field)) {
                                writer.closeBlock("}");
                                if (i < listFields.size() - 1) {
                                    writer.write("");
                                }
                            }
                        }
                    }
                );
            }
        });
    }

    private void emitWriteVarints() {
        writer.openBlock("private void writeVarints($T s) {", "}\n", CommonSymbols.SparrowhawkSerializer, () -> {
            for (int j = 0; j < index.getVarintFieldSetCount(shape); j++) {
                int fieldSetIdx = j;
                writer.openBlock(
                    "if ($$varint_$L != $L) {",
                    "}",
                    fieldSetIdx,
                    bitsToString(getEmpty(KConstants.T_VARINT, fieldSetIdx)),
                    () -> {
                        writer.write("s.writeVarUL($$varint_$L);", fieldSetIdx);
                        if (fieldSetIdx > 0) {
                            writer.write("s.writeVarUI($L);", fieldSetIdx - 1);
                        }
                        for (var field : index.getVarintMembers(shape, fieldSetIdx)) {
                            if (isOptional(field)) {
                                writer.openBlock("if (has$L()) {", methodNameForField(field));
                            }
                            writer.write("s.write$L($L);", capitalize(varintSerializeMethod(field)), fieldName(field));
                            if (isOptional(field)) {
                                writer.closeBlock("}");
                            }
                        }
                    }
                );
            }
        });
    }

    private void emitFixedWidthEncoder(
        int width,
        String fieldsetPrefix,
        String method,
        Function<ToShapeId, Integer> fieldSetCount,
        BiFunction<ToShapeId, Integer, List<MemberShape>> fieldFn
    ) {
        writer.openBlock(
            "private void write$LByteFields($T s) {",
            "}\n",
            width == KConstants.T_FOUR ? "Four" : "Eight",
            CommonSymbols.SparrowhawkSerializer,
            () -> {
                for (int j = 0; j < fieldSetCount.apply(shape); j++) {
                    int fieldSetIdx = j;
                    writer.openBlock(
                        "if ($$$L_$L != $L) {",
                        "}",
                        fieldsetPrefix,
                        fieldSetIdx,
                        bitsToString(getEmpty(width, fieldSetIdx)),
                        () -> {
                            writer.write("s.writeVarUL($$$L_$L);", fieldsetPrefix, fieldSetIdx);
                            if (fieldSetIdx > 0) {
                                writer.write("s.writeVarUI($L);", fieldSetIdx - 1);
                            }
                            for (var field : fieldFn.apply(shape, fieldSetIdx)) {
                                if (isOptional(field)) {
                                    writer.openBlock("if (has$L()) {", methodNameForField(field));
                                }
                                String m = method;
                                if (model.expectShape(field.getTarget()).isTimestampShape()) {
                                    m = settings.useInstant() ? "Instant" : "Date";
                                }
                                writer.write("s.write$L($L);", m, fieldName(field));
                                if (isOptional(field)) {
                                    writer.closeBlock("}");
                                }
                            }
                        }
                    );
                }
            }
        );
    }

    private void generateEncodeTo() {
        writer.write("s.writeVarUL($T(size()));", encodeByteListLength);
        if (index.hasVarintMembers(shape)) {
            writer.write("writeVarints(s);");
        }

        if (index.hasFourByteMembers(shape)) {
            writer.write("writeFourByteFields(s);");
        }

        if (index.hasEightByteMembers(shape)) {
            writer.write("writeEightByteFields(s);");
        }

        if (index.hasListMembers(shape)) {
            writer.write("writeListFields(s);");
        }
    }

    private void generateDecoder() {
        generateMethod("public void decodeFrom($T d)", CommonSymbols.SparrowhawkDeserializer, this::generateDecodeFrom);
        if (index.hasVarintMembers(shape)) {
            int varintFieldSetCount = index.getVarintFieldSetCount(shape);
            if (varintFieldSetCount > 1) {
                generateMethod(
                    "private void decodeVarintFieldSet($T d, int fieldSetIdx, long fieldSet)",
                    CommonSymbols.SparrowhawkDeserializer,
                    this::emitVarintDecodeMethod
                );
            }
            for (int i = 0; i < varintFieldSetCount; i++) {
                writeVarintFieldsetDecode(i, index.getVarintMembers(shape, i));
            }
        }
        if (index.hasFourByteMembers(shape)) {
            int fourByteFieldSetCount = index.getFourByteFieldSetCount(shape);
            if (fourByteFieldSetCount > 1) {
                generateMethod(
                    "private void decodeFourByteFieldSet($T d, int fieldSetIdx, long fieldSet)",
                    CommonSymbols.SparrowhawkDeserializer,
                    this::emitFourByteDecodeMethod
                );
            }
            for (int i = 0; i < fourByteFieldSetCount; i++) {
                emitFourByteFieldSetDecoderMethod(i);
            }
        }
        if (index.hasEightByteMembers(shape)) {
            int eightByteFieldSetCount = index.getEightByteFieldSetCount(shape);
            if (eightByteFieldSetCount > 1) {
                generateMethod(
                    "private void decodeEightByteFieldSet($T d, int fieldSetIdx, long fieldSet)",
                    CommonSymbols.SparrowhawkDeserializer,
                    this::emitEightByteDecodeMethod
                );
            }
            for (int i = 0; i < eightByteFieldSetCount; i++) {
                emitEightByteFieldSetDecoderMethod(i);
            }
        }
        if (index.hasListMembers(shape)) {
            int listFieldSetCount = index.getListFieldSetCount(shape);
            if (listFieldSetCount > 1) {
                generateMethod(
                    "private void decodeListFieldSet($T d, int fieldSetIdx, long fieldSet)",
                    CommonSymbols.SparrowhawkDeserializer,
                    this::emitListDecodeMethod
                );
            }
            for (int i = 0; i < listFieldSetCount; i++) {
                emitListFieldSetDecoderMethod(i);
            }
        }
    }

    private void emitDecoderPrelude(String fieldSetName, String requiredFields, String type) {
        writer.write("""
            SparrowhawkDeserializer.checkFields(fieldSet, $L, "$L");
            this.$L = fieldSet;""", requiredFields, type, fieldSetName);
    }

    private void emitVarintDecodeMethod() {
        writer.openBlock("switch (fieldSetIdx) {", """
                default: throw new IllegalArgumentException("Unknown fieldSet index " + fieldSetIdx);
            }""", () -> {
            for (int i = 0; i < index.getVarintFieldSetCount(shape); i++) {
                int fieldSetIdx = i;
                writer.openBlock(
                    "case $L:",
                    "    break;",
                    fieldSetIdx,
                    () -> writer.write("decodeVarintFieldSet$L(d, fieldSet);", fieldSetIdx)
                );
            }
        });
    }

    private void writeVarintFieldsetDecode(int fieldSetIdx, List<MemberShape> varintMembers) {
        writer.openBlock(
            "private void decodeVarintFieldSet$L($T d, long fieldSet) {",
            "}\n",
            fieldSetIdx,
            CommonSymbols.SparrowhawkDeserializer,
            () -> {
                emitDecoderPrelude("$varint_" + fieldSetIdx, "REQUIRED_VARINT_" + fieldSetIdx, "varint");
                for (var field : varintMembers) {
                    if (isOptional(field)) {
                        writer.openBlock("if (has$L()) {", methodNameForField(field));
                    }
                    writer.write("this.$L = d.$L();", fieldName(field), varintSerializeMethod(field));
                    if (isOptional(field)) {
                        writer.closeBlock("}");
                    }
                }
            }
        );
    }

    public boolean isOptional(MemberShape field) {
        return !isRequired(field);
    }

    public boolean isRequired(MemberShape field) {
        if (shape.isUnionShape()) return false;
        return field.expectTrait(SparrowhawkFieldTrait.class).isRequired();
    }

    private void emitFixedWidthDecoder(
        String width,
        int fieldSetCount
    ) {
        writer.openBlock("switch (fieldSetIdx) {", """
                default: throw new IllegalArgumentException("Unknown fieldSet index " + fieldSetIdx);
            }""", () -> {
            for (int i = 0; i < fieldSetCount; i++) {
                int fieldSetIdx = i;
                writer.openBlock(
                    "case $L:",
                    "    break;",
                    fieldSetIdx,
                    () -> writer.write("decode$LByteFieldSet$L(d, fieldSet);", capitalize(width), fieldSetIdx)
                );
            }
        });
    }

    private void emitFixedWidthFieldSetDecoder(
        String width,
        String method,
        int fieldSetIdx,
        List<MemberShape> fields
    ) {
        writer.openBlock(
            "private void decode$LByteFieldSet$L($T d, long fieldSet) {",
            "}\n",
            capitalize(width),
            fieldSetIdx,
            CommonSymbols.SparrowhawkDeserializer,
            () -> {
                emitDecoderPrelude(
                    "$" + width + "Byte_" + fieldSetIdx,
                    "REQUIRED_" + upperCase(width) + "_BYTE_" + fieldSetIdx,
                    width + "-byte"
                );
                for (MemberShape field : fields) {
                    if (isOptional(field)) {
                        writer.openBlock("if (has$L()) {", methodNameForField(field));
                    }
                    String m = method;
                    if (model.expectShape(field.getTarget()).isTimestampShape()) {
                        m = settings.useInstant() ? "instant" : "date";
                    }
                    writer.write("this.$L = d.$L();", fieldName(field), m);
                    if (isOptional(field)) {
                        writer.closeBlock("}");
                    }
                }
            }
        );
    }

    private void emitFourByteDecodeMethod() {
        emitFixedWidthDecoder("four", index.getFourByteFieldSetCount(shape));
    }

    private void emitFourByteFieldSetDecoderMethod(int fieldSetIdx) {
        emitFixedWidthFieldSetDecoder("four", "f4", fieldSetIdx, index.getFourByteMembers(shape, fieldSetIdx));
    }

    private void emitEightByteDecodeMethod() {
        emitFixedWidthDecoder("eight", index.getEightByteFieldSetCount(shape));
    }

    private void emitEightByteFieldSetDecoderMethod(int fieldSetIdx) {
        emitFixedWidthFieldSetDecoder("eight", "d8", fieldSetIdx, index.getEightByteMembers(shape, fieldSetIdx));
    }

    private void emitListDecodeMethod() {
        writer.openBlock("switch (fieldSetIdx) {", """
                default: throw new IllegalArgumentException("Unknown fieldSet index " + fieldSetIdx);
            }""", () -> {
            for (int i = 0; i < index.getListFieldSetCount(shape); i++) {
                int fieldSetIdx = i;
                writer.openBlock(
                    "case $L:",
                    "    break;",
                    fieldSetIdx,
                    () -> writer.write("decodeListFieldSet$L(d, fieldSet);", fieldSetIdx)
                );
            }
        });

    }

    private void emitListFieldSetDecoderMethod(int fieldSetIdx) {
        writer.openBlock(
            "private void decodeListFieldSet$L($T d, long fieldSet) {",
            "}\n",
            fieldSetIdx,
            CommonSymbols.SparrowhawkDeserializer,
            () -> {//TODO: check the sublist, not all lists
                if (index.hasRequiredLists(shape)) {
                    writer.write(
                        "$T.checkFields(fieldSet, REQUIRED_LIST_$L, \"lists\");",
                        CommonSymbols.SparrowhawkDeserializer,
                        fieldSetIdx
                    );
                }
                writer.write("this.$$list_$L = fieldSet;", fieldSetIdx);
                for (var field : index.getListMembers(shape, fieldSetIdx)) {
                    if (isOptional(field)) {
                        writer.openBlock("if (has$L()) {", methodNameForField(field));
                    } else {
                        writer.openBlock("{");
                    }

                    var fieldSymbol = symbolProvider.toSymbol(field);
                    var shape = model.expectShape(field.getTarget());
                    var fieldName = fieldName(field);
                    if (shape.isBlobShape()) {
                        writer.write("this.$L = d.bytes();", fieldName);
                    } else if (isString(shape)) {
                        writer.write("this.$L = d.string();", fieldName);
                    } else if (shape.isMapShape()) {
                        var valueSymbol = mapValueTarget(model.expectShape(field.getTarget()));
                        writer.write(
                            """
                                $1T m = new $1T($2C);
                                m.decodeFrom(d);
                                this.$3L = m;""",
                            fieldSymbol.expectProperty("sparrowhawkCollection", SymbolReference.class),
                            writer.consumer(w -> {
                                if (isStructure(valueSymbol.expectProperty("shape", Shape.class))) {
                                    w.writeInline("$T::new", valueSymbol);
                                }
                            }),
                            fieldName
                        );
                    } else if (shape.isListShape()) {
                        var valueSymbol = listTarget(model.expectShape(field.getTarget()));
                        var valueType = valueSymbol.expectProperty("shape", Shape.class);
                        if (isString(valueType)) {
                            var listType = isSparse(field) ? SparseStringList : StringList;
                            writer.write("""
                                $1T l = new $1T();
                                l.decodeFrom(d);
                                this.$2L = l;""", listType, fieldName);
                        } else if (isVarintShape(valueType) || valueType.isFloatShape() || valueType.isDoubleShape()) {
                            writer.write("this.$L = d.decode$TList();", fieldName, valueSymbol);
                        } else if (isStructure(valueType)) {
                            var temporaryArrayName = String.format("%sArr", fieldName);
                            writer.write("int $LLen = $T(d.varUL());", fieldName, decodeLenPrefixedListLengthChecked);
                            writer.write("$1T[] $2L = new $1T[$3LLen];", valueSymbol, temporaryArrayName, fieldName);
                            writer.write("for (int i = 0; i < $LLen; i++) {", fieldName);
                            writer.indent().write("$1T x = new $1T();", valueSymbol);
                            writer.write("x.decodeFrom(d);");
                            writer.write("$L[i] = x;", temporaryArrayName);
                            writer.dedent().write("}");
                            writer.write("this.$L = $T($L);", fieldName, asList, temporaryArrayName);
                        } else {
                            throw new RuntimeException("can't handle: " + field);
                        }
                    } else if (isStructure(shape)) {
                        writer.write("""
                            $1T obj = new $1T();
                            obj.decodeFrom(d);
                            this.$2L = obj;""", fieldSymbol, fieldName);
                    } else {
                        throw new RuntimeException("no decoder for: " + field);
                    }

                    writer.closeBlock("}");
                }

            }
        );
    }

    private static boolean isVarintShape(Shape type) {
        return switch (type.getType()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM, LONG -> true;
            default -> false;
        };
    }

    private String varintSerializeMethod(MemberShape field) {
        return varintSerializeMethod(model.expectShape(field.getTarget()).getType());
    }

    private static String varintSerializeMethod(ShapeType shapeType) {
        return switch (shapeType) {
            case LONG -> "varL";
            case INTEGER, INT_ENUM -> "varI";
            case BOOLEAN -> "bool";
            case BYTE -> "varB";
            case SHORT -> "varS";
            default -> throw new RuntimeException("not a varint: " + shapeType);
        };
    }

    private void generateDecodeFrom() {
        writer.openBlock("""
            int size = (int) $T(d.varUI());
            this.$$size = size;
            int start = d.pos();

            while ((d.pos() - start) < size) {""", "}", decodeElementCount, () -> {
            writer.write("""
                long fieldSet = d.varUL();
                int fieldSetIdx = ((fieldSet & 0b100) != 0) ? d.varUI() + 1 : 0;
                int type = (int) (fieldSet & 3);""");
            int emitted = 0;
            if (index.hasListMembers(shape)) {
                emitted++;
                writer.write("if (type == $T) {", T_LIST).indent();
                if (index.getListFieldSetCount(shape) > 1) {
                    writer.write("decodeListFieldSet(d, fieldSetIdx, fieldSet);");
                } else {
                    // TODO: skip unknown fieldsets!
                    writer.write(
                        "if (fieldSetIdx != 0) { throw new IllegalArgumentException(\"unknown fieldSetIdx \" + fieldSetIdx); }"
                    );
                    writer.write("decodeListFieldSet0(d, fieldSet);");
                }
            }
            if (index.hasVarintMembers(shape)) {
                if (emitted++ > 0) {
                    writer.dedent().writeInline("} else ");
                }
                writer.write("if (type == $T) {", T_VARINT).indent();
                if (index.getVarintFieldSetCount(shape) > 1) {
                    writer.write("decodeVarintFieldSet(d, fieldSetIdx, fieldSet);");
                } else {
                    // TODO: skip unknown fieldsets!
                    writer.write(
                        "if (fieldSetIdx != 0) { throw new IllegalArgumentException(\"unknown fieldSetIdx \" + fieldSetIdx); }"
                    );
                    writer.write("decodeVarintFieldSet0(d, fieldSet);");
                }
            }
            if (index.hasFourByteMembers(shape)) {
                if (emitted++ > 0) {
                    writer.dedent().writeInline("} else ");
                }
                writer.write("if (type == $T) {", T_FOUR).indent();
                if (index.getFourByteFieldSetCount(shape) > 1) {
                    writer.write("decodeFourByteFieldSet(d, fieldSetIdx, fieldSet);");
                } else {
                    // TODO: skip unknown fieldsets!
                    writer.write(
                        "if (fieldSetIdx != 0) { throw new IllegalArgumentException(\"unknown fieldSetIdx \" + fieldSetIdx); }"
                    );
                    writer.write("decodeFourByteFieldSet0(d, fieldSet);");
                }
            }
            if (index.hasEightByteMembers(shape)) {
                if (emitted++ == 3) {
                    writer.dedent().write("} else {").indent();
                } else {
                    if (emitted > 1) {
                        writer.dedent().writeInline("} else ");
                    }
                    writer.write("if (type == $T) {", T_EIGHT).indent();
                }
                if (index.getEightByteFieldSetCount(shape) > 1) {
                    writer.write("decodeEightByteFieldSet(d, fieldSetIdx, fieldSet);");
                } else {
                    // TODO: skip unknown fieldsets!
                    writer.write(
                        "if (fieldSetIdx != 0) { throw new IllegalArgumentException(\"unknown fieldSetIdx \" + fieldSetIdx); }"
                    );
                    writer.write("decodeEightByteFieldSet0(d, fieldSet);");
                }
            }
            if (emitted > 0) {
                writer.dedent();
            }
            if (emitted == 4) {
                writer.write("}");
            } else {
                if (emitted > 0) {
                    writer.writeInline("}");
                    writer.openBlock(" else {");
                }
                writer.write("""
                    throw new RuntimeException("Unexpected field set type: " + type);""");
                if (emitted > 0) {
                    writer.closeBlock("}");
                }
            }
        });
    }

    private void generateEquals() {
        writer.openBlock("@Override\npublic boolean equals(Object other) {", "}", () -> {
            writer.write("if (this == other) return true;");
            writer.write("if (!(other instanceof $L)) return false;", symbol.getName());
            writer.write("$1L o = ($1L) other;", symbol.getName());
            generateEqualsForFields(getAllVarintMembers());
            generateEqualsForFields(getAllFourByteMembers());
            generateEqualsForFields(getAllEightByteMembers());
            getAllListMembers().forEach(field -> {
                // null implies hasField() is false, so we don't need to explicitly call it on both objects
                writer.openBlock(
                    "if (!$2T.equals(get$1L(), o.get$1L())) {",
                    "}",
                    methodNameForField(field),
                    Objects,
                    () -> {
                        writer.write("return false;");
                    }
                );
            });
            writer.write("return true;");
        });
    }

    private void generateEqualsForFields(Stream<MemberShape> fields) {
        fields.forEach(field -> {
            if (model.expectShape(field.getTarget()).isTimestampShape()) {
                writer.openBlock(
                    "if (!$2T.equals(get$1L(), o.get$1L())) {",
                    "}",
                    methodNameForField(field),
                    Objects,
                    () -> {
                        writer.write("return false;");
                    }
                );
                return;
            }

            if (isOptional(field)) {
                writer.openBlock("if (has$1L() == o.has$1L()) {", methodNameForField(field));
            } else {
                writer.openBlock("if ($1L != o.$1L) {", "}", fieldName(field), () -> {
                    writer.write("return false;");
                });
            }
            if (isOptional(field)) {
                writer.closeBlock("} else {");
                writer.indent().write("return false;").dedent().write("}");
            }
        });
    }

    private static long getEmpty(int type, int fieldSetIdx) {
        if (fieldSetIdx == 0) {
            return type;
        }
        return 0b100 | type;
    }

    private static String bitsToString(long l) {
        return "0x" + Long.toHexString(l) + "L";
    }

    private boolean isSparse(MemberShape memberShape) {
        return model.expectShape(memberShape.getTarget()).hasTrait(SparseTrait.class);
    }

    private record FieldSet(String name, FieldType type, int fieldSetIdx) {}

    public Stream<MemberShape> getAllFourByteMembers() {
        Stream<MemberShape> allFourByteMembers = Stream.empty();
        for (int i = 0; i < index.getFourByteFieldSetCount(shape); i++) {
            allFourByteMembers = Stream.concat(allFourByteMembers, index.getFourByteMembers(shape, i).stream());
        }
        return allFourByteMembers;
    }

    public Stream<MemberShape> getAllEightByteMembers() {
        Stream<MemberShape> allEightByteMembers = Stream.empty();
        for (int i = 0; i < index.getEightByteFieldSetCount(shape); i++) {
            allEightByteMembers = Stream.concat(allEightByteMembers, index.getEightByteMembers(shape, i).stream());
        }
        return allEightByteMembers;
    }

    public Stream<MemberShape> getAllVarintMembers() {
        Stream<MemberShape> allVarintMembers = Stream.empty();
        for (int i = 0; i < index.getVarintFieldSetCount(shape); i++) {
            allVarintMembers = Stream.concat(allVarintMembers, index.getVarintMembers(shape, i).stream());
        }
        return allVarintMembers;
    }

    public Stream<MemberShape> getAllListMembers() {
        Stream<MemberShape> allListMembers = Stream.empty();
        for (int i = 0; i < index.getListFieldSetCount(shape); i++) {
            allListMembers = Stream.concat(allListMembers, index.getListMembers(shape, i).stream());
        }
        return allListMembers;
    }
}

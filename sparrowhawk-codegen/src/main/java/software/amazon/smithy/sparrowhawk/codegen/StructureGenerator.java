/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.Objects;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.StringList;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_EIGHT;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_FOUR;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_LIST;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.T_VARINT;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.byteListLengthEncodedSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeByteListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeEightBListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeFourBListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.encodeVarintListLength;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.imp;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.intSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.longSize;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.missingField;
import static software.amazon.smithy.sparrowhawk.codegen.CommonSymbols.ulongSize;
import static software.amazon.smithy.sparrowhawk.codegen.Util.isStructure;
import static software.amazon.smithy.utils.StringUtils.capitalize;
import static software.amazon.smithy.utils.StringUtils.upperCase;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.protocol.traits.SparrowhawkObjectTrait;
import software.amazon.smithy.sparrowhawk.codegen.CodeSections.EndClassSection;
import software.amazon.smithy.sparrowhawk.codegen.CodeSections.StartClassSection;

public final class StructureGenerator implements Runnable {
    private static final Map<String, Object> DEFAULT_REFERENCES;

    static {
        DEFAULT_REFERENCES = new HashMap<>();
        try {
            for (Field f : CommonSymbols.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == SymbolReference.class) {
                    // apparently smithy context keys have to start with a lowercase letter
                    if (DEFAULT_REFERENCES.put(lowercaseFirstLetter(f.getName()), f.get(null)) != null) {
                        throw new RuntimeException("duplicate context key for " + f.getName());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String lowercaseFirstLetter(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Symbol symbol;
    private final Model model;
    private final SparrowhawkIndex index;
    private final List<FieldSet> fieldSets = new ArrayList<>();
    private final JavaWriter writer;
    private final SparrowhawkSettings settings;

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
        this.symbol = symbolProvider.toSymbol(shape);
        this.writer = writer;
        this.index = SparrowhawkIndex.of(model);
        this.settings = settings;
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
        writer.pushState();
        writer.putContext(DEFAULT_REFERENCES);
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
        writer.popState();
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

        writer.pushState();
        var requiredFields = "REQUIRED_" + fieldType.uppercaseId + "_" + secIdx;
        writer.putContext("requiredFields", requiredFields);
        writer.write("private static final long ${requiredFields:L} = $L;", bitsToString(required));

        // number of unknown fields is 64 - number of known fields - 3 bits for type
        var unknown = fields.size() == 61 ? 0 : -1L << (fields.size() + 3);
        if (unknown != 0) {
            var unknownMask = "UNKNOWN_MASK_" + fieldType.uppercaseId + "_" + secIdx;
            writer.putContext("unknownMask", unknownMask);
            writer.write("private static final long ${unknownMask:L} = $L;", bitsToString(unknown));
        }

        var fieldsetName = "$" + fieldType.lowercaseId + "_" + secIdx;
        writer.putContext("fieldsetName", fieldsetName);
        fieldSets.add(new FieldSet(fieldsetName, fieldType, secIdx));
        writer.write("private long ${fieldsetName:L} = ${requiredFields:L};");

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

        writer.pushState();
        writer.putContext("fieldName", fieldName);
        writer.putContext("fieldSymbol", fieldSymbol);

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
            writer.pushState();
            if (isString(target)) {
                writer.write("""
                    if (${fieldName:L} == null) {
                        return null;
                    }
                    if (${fieldName:L} instanceof String) {
                        return (String) ${fieldName:L};
                    }
                    String s = new String((byte[]) ${fieldName:L}, ${uTF_8:T});
                    this.${fieldName:L} = s;
                    return s;""");
            } else if (isBigInteger(target)) {
                writer.putContext("bigInteger", imp("java.math", "BigInteger"));
                writer.write("""
                    if (${fieldName:L} == null) {
                        return null;
                    }
                    if (${fieldName:L} instanceof ${bigInteger:T}) {
                        return (${bigInteger:T}) ${fieldName:L};
                    }
                    ${bigInteger:T} bi = new ${bigInteger:T}((byte[]) ${fieldName:L});
                    this.${fieldName:L} = bi;
                    return bi;""");
            } else if (target.isBigDecimalShape()) {
                writer.putContext("bigDecimal", imp("java.math", "BigDecimal"));
                writer.write("""
                    if (${fieldName:L} == null) {
                        return null;
                    }
                    if (${fieldName:L} instanceof ${sparrowhawkBigDecimalHolder:T}) {
                        ${bigDecimal:T} bd = ((${sparrowhawkBigDecimalHolder:T}) ${fieldName:L}).toBigDecimal();
                        ${fieldName:L} = bd;
                        return bd;
                    }
                    return (${bigDecimal:T}) ${fieldName:L};""");
            } else if (target.isMapShape()) {
                var sparrowhawkCollectionSymbol = fieldSymbol.expectProperty(
                    "sparrowhawkCollection",
                    SymbolReference.class
                );
                setupSparrowhawkCollectionConstructor(fieldSymbol);
                writer.putContext("sparrowhawkCollection", sparrowhawkCollectionSymbol);
                writer.write(
                    """
                        Object field = ${fieldName:L};
                        if (field == null) return null;
                        if (field.getClass() == ${sparrowhawkCollection:T}.class) {
                            ${fieldSymbol:T} m = ((${sparrowhawkCollection:T}) field).to${?nestingLevel}Nested${/nestingLevel}Map(${?nestingLevel}${nestingLevel:L}${/nestingLevel});
                            this.${fieldName:L} = m;
                            return m;
                        }
                        return (${fieldSymbol:T}) ${fieldName:L};"""
                );
            } else if (target.isListShape()) {
                var valueSymbol = listTarget(model.expectShape(field.getTarget()));
                var valueType = valueSymbol.expectProperty("shape", Shape.class);

                var listImplTypeOpt = fieldSymbol.getProperty("listImplType");
                if (listImplTypeOpt.isPresent()) {
                    writer.putContext("listImplType", listImplTypeOpt.get());
                    writer.putContext("value", fieldSymbol.expectProperty("value"));
                    writer.putContext("simple", fieldSymbol.expectProperty("simple"));
                    writer.write("""
                        Object field = ${fieldName:L};
                        if (field == null) return null;
                        if (field.getClass() == ${listImplType:T}.class) {${?simple}
                            return ((${listImplType:T}) field).toList();
                        ${/simple}${^simple}
                            ${fieldSymbol:T} m = ((${listImplType:T}) field).toList();
                            this.${fieldName:L} = m;
                            return m;
                        ${/simple}
                        }
                        return (${fieldSymbol:T}) field;""");
                } else {
                    writer.write("return ${fieldName:L};");
                }
            } else {
                writer.write("return $L;", fieldName);
            }
            writer.popState();
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

        writer.popState();
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
            writer.pushState();
            writer.putContext("optional", isOptional(field));
            writer.putContext("methodName", methodNameForField(field));
            writer.putContext("sizer", writer.consumer(w -> sizer.accept(field)));
            writer.write("""
                ${?optional}if (has${methodName:L}()) {
                    ${/optional}${sizer:C|}${?optional}
                }${/optional}""");
            writer.popState();
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
                    writer.pushState();
                    if (isOptional(field)) {
                        writer.openBlock("if (has$L()) {", methodNameForField(field));
                    }

                    var shape = model.expectShape(field.getTarget());
                    var fieldName = fieldName(field);
                    if (shape.isBlobShape()) {
                        if (shape.hasTrait(SparrowhawkObjectTrait.class)) {
                            writer.write("size += $L.remaining();", fieldName);
                        } else {
                            writer.write("size += $T($L.remaining());", byteListLengthEncodedSize, fieldName);
                        }
                    } else if (isStructure(shape)) {
                        writer.write("size += $T($L.size());", byteListLengthEncodedSize, fieldName);
                    } else {
                        Sizer sizer;
                        if (shape.isListShape()) {
                            var listType = model.expectShape(
                                ((ListShape) model.expectShape(field.getTarget())).getMember().getTarget()
                            );
                            if (shape.hasTrait(SparseTrait.class)) {
                                sizer = switch (listType.getType()) {
                                    case STRUCTURE, UNION -> new SparseStructureListSizer(field, listType);
                                    case BLOB -> new SparseBlobListSizer(field);
                                    default -> new SparseListSizer(field, listType);
                                };
                            } else {
                                sizer = switch (listType.getType()) {
                                    case STRING, ENUM -> new StringListSizer(field);
                                    case BLOB -> new BlobListSizer(field);
                                    case BYTE, SHORT, INTEGER, LONG, BOOLEAN, INT_ENUM, FLOAT, DOUBLE, TIMESTAMP ->
                                        new SimpleListSizer(field, listType);
                                    case STRUCTURE, UNION -> new StructureListSizer(field);
                                    default ->
                                        throw new IllegalStateException("Unexpected value: " + listType.getType());
                                };
                            }
                        } else if (isString(shape)) {
                            sizer = new StringSizer(field);
                        } else if (isBigInteger(shape)) {
                            sizer = new BigIntegerSizer(field);
                        } else if (shape.isBigDecimalShape()) {
                            sizer = new BigDecimalSizer(field);
                        } else if (shape.isMapShape()) {
                            sizer = new MapSizer(field);
                        } else {
                            throw new RuntimeException("Bad list type: " + shape.getType());
                        }

                        extraSizers.add(sizer);
                        writer.write("size += $L();", sizer.methodName());
                    }

                    if (isOptional(field)) {
                        writer.closeBlock("}");
                    }

                    writer.popState();
                }
            }

            writer.write("return size;");
        });

        extraSizers.forEach(Sizer::generate);
    }

    private abstract class Sizer extends ShapeVisitor.Default<Void> {
        protected final MemberShape field;

        private Sizer(MemberShape field) {
            this.field = field;
        }

        String methodName() {
            return "$" + field.getMemberName() + "Size";
        }

        final void generate() {
            writer.pushState();
            writer.putContext("fieldName", fieldName(field));
            writer.putContext("required", isRequired(field));
            writer.putContext("methodName", methodName());
            writer.openBlock("""
                private int ${methodName:L}() {${?required}
                    if (${fieldName:L} == null) {
                        ${missingField:T}("Required field '${fieldName:L}' is missing");
                    }${/required}""", "}\n", this::generate0);
            writer.popState();
        }

        abstract void generate0();

        @Override
        protected final Void getDefault(Shape shape) {
            throw new RuntimeException(this + " does not support " + shape);
        }
    }

    private final class MapSizer extends Sizer {
        private MapSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            var mapSymbol = symbolProvider.toSymbol(field);
            writer.putContext("mapSymbol", mapSymbol);
            writer.putContext("byteListLengthEncodedSize", byteListLengthEncodedSize);
            setupSparrowhawkCollectionConstructor(mapSymbol);
            writer.write("Object field = ${fieldName:L};");
            if (isRequired(field)) {
                writer.openBlock("if (field == null) {", "}", () -> {
                    writer.write(
                        "$2T(\"Required field '$1L' is missing\");",
                        field.getMemberName(),
                        missingField
                    );
                });
            }

            writer.write(
                """
                    int size;
                    if (field.getClass() == ${sparrowhawkCollection:T}.class) {
                        size = ((${sparrowhawkCollection:T}) field).size();
                    } else {
                        ${sparrowhawkCollection:T} m = new ${sparrowhawkCollection:T}(${ctor:C});
                        m.from${?nestingLevel}Nested${/nestingLevel}Map((${mapSymbol:T}) field${?nestingLevel}, ${nestingLevel:L}, ${supp:C}${/nestingLevel});
                        this.${fieldName:L} = m;
                        size = m.size();
                    }
                    return ${byteListLengthEncodedSize:T}(size);"""
            );
        }
    }

    private void setupSparrowhawkCollectionConstructor(Symbol symbol) {
        var sparrowhawkCollectionSymbol = symbol.expectProperty("sparrowhawkCollection", SymbolReference.class);
        writer.putContext("sparrowhawkCollection", sparrowhawkCollectionSymbol);

        var nestingOpt = symbol.getProperty("nesting", List.class);
        if (nestingOpt.isPresent()) {
            var nesting = nestingOpt.get();
            writer.putContext("nestedCollections", nesting);
            writer.putContext("fromNestedSupplier", symbol.expectProperty("fromNestedSupplier", List.class));
            writer.putContext("nestingLevel", symbol.expectProperty("nestingLevel", Integer.class));
            writer.putContext("supp", writer.consumer(w -> {
                w.writeInline("${#fromNestedSupplier}() -> new ${value:T}(${/fromNestedSupplier}");
                w.writeInline("${#fromNestedSupplier})${/fromNestedSupplier}");
            }));
            writer.putContext("ctor", writer.consumer(w -> {
                w.writeInline("${#nestedCollections}() -> new ${value:T}(${/nestedCollections}");
                w.writeInline("${#nestedCollections})${/nestedCollections}");
            }));
        } else {
            writer.putContext("ctor", writer.consumer(w -> {
                var valueSymbol = symbol.expectProperty("value", Symbol.class);
                if (isStructure(valueSymbol.expectProperty("shape", Shape.class))) {
                    w.writeInline("$T::new", valueSymbol);
                }
            }));
        }
    }

    private static boolean isString(Shape shape) {
        return shape.isStringShape() || shape.isEnumShape();
    }

    private static boolean isBigInteger(Shape shape) {
        return shape.isBigIntegerShape();
    }

    private final class SparseListSizer extends Sizer {
        private final Shape element;

        private SparseListSizer(MemberShape field, Shape element) {
            super(field);
            this.element = element;
        }

        @Override
        void generate0() {
            writer.putContext("size", "_size");
            writer.putContext("len", "_len");
            var fieldSym = symbolProvider.toSymbol(field);
            writer.putContext("listType", fieldSym);
            writer.putContext("listImplType", fieldSym.expectProperty("listImplType"));
            writer.write("""
                ${listImplType:T} _list;
                if (${fieldName:L}.getClass() == ${listImplType:T}.class) {
                    _list = (${listImplType:T}) ${fieldName:L};
                } else {
                    _list = ${listImplType:T}.fromList((${listType:T}) ${fieldName:L});
                    this.${fieldName:L} = _list;
                }
                return ${lenPrefixedListLengthEncodedSize:T}(_list.size(), _list.elementCount());""");
        }
    }

    private final class SparseStructureListSizer extends Sizer {
        private final Shape element;

        private SparseStructureListSizer(MemberShape field, Shape element) {
            super(field);
            this.element = element;
        }

        @Override
        void generate0() {
            writer.write("return ${sparrowhawkSerializer:T}.sparseObjectListSize(${fieldName:L});");
        }
    }

    private final class SparseBlobListSizer extends Sizer {
        private SparseBlobListSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.write("return ${sparrowhawkSerializer:T}.sparseBlobListSize(${fieldName:L});");
        }
    }

    private final class SimpleListSizer extends Sizer {
        private final Shape element;

        private SimpleListSizer(MemberShape field, Shape element) {
            super(field);
            this.element = element;
        }

        @Override
        void generate0() {
            writer.putContext("size", "_size");
            writer.putContext("len", "_len");
            writer.putContext("listLengthEncoder", switch (element.getType()) {
                case BOOLEAN, BYTE, SHORT, INTEGER, LONG, INT_ENUM -> encodeVarintListLength;
                case FLOAT -> encodeFourBListLength;
                case DOUBLE, TIMESTAMP -> encodeEightBListLength;
                default -> throw new RuntimeException("not a simple list: " + field);
            });
            writer.putContext("elementSizeEncoder", switch (element.getType()) {
                case BOOLEAN, BYTE, SHORT, INTEGER, LONG, INT_ENUM -> writer.consumer(this::varint);
                case FLOAT -> writer.consumer(this::four);
                case DOUBLE, TIMESTAMP -> writer.consumer(this::eight);
                default -> throw new RuntimeException("not a simple list: " + field);
            });
            writer.write("""
                int ${len:L} = ${fieldName:L}.size();
                int ${size:L} = ${uintSize:T}(${listLengthEncoder:T}(${len:L}));
                ${elementSizeEncoder:C|}
                return ${size:L};""");
        }

        private void varint(JavaWriter writer) {
            writer.putContext("varintSize", switch (element.getType()) {
                case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM -> intSize;
                case LONG -> longSize;
                default -> throw new RuntimeException("not a varint: " + field);
            });
            writer.write("""
                for (int _i = 0; _i < ${len:L}; _i++) {
                    ${size:L} += ${varintSize:T}(${fieldName:L}.get(_i));
                }""");
        }

        private void four(JavaWriter writer) {
            writer.write("${size:L} += 4 * ${len:L};");
        }

        private void eight(JavaWriter writer) {
            writer.write("${size:L} += 8 * ${len:L};");
        }
    }

    private final class StringListSizer extends Sizer {
        StringListSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.putContext("listType", StringList);
            writer.write("""
                ${listType:T} _list;
                if (${fieldName:L}.getClass() == ${listType:T}.class) {
                    _list = (${listType:T}) ${fieldName:L};
                } else {
                    _list = ${listType:T}.fromList((${list:T}<String>) ${fieldName:L});
                    this.${fieldName:L} = _list;
                }
                return ${lenPrefixedListLengthEncodedSize:T}(_list.size(), _list.elementCount());""");
        }
    }

    private final class BlobListSizer extends Sizer {
        private BlobListSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.pushState();
            writer.putContext("methodName", methodName());
            writer.putContext("fieldName", fieldName(field));
            writer.putContext("required", isRequired(field));
            writer.write("""
                return ${sparrowhawkSerializer:T}.blobListEncodedSize(${fieldName:L});
                """);
            writer.popState();
        }
    }

    private final class StringSizer extends Sizer {
        StringSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.write("int size;");
            // TODO: is this faster than instanceof?
            writer.write("if (${fieldName:L}.getClass() == byte[].class) {");
            writer.indent().write("size = ((byte[]) ${fieldName:L}).length;");
            writer.dedent().write("} else {");
            writer.indent().write("""
                byte[] bytes = ((String) ${fieldName:L}).getBytes(${uTF_8:T});
                this.${fieldName:L} = bytes;
                size = bytes.length;""");
            writer.dedent().write("}\n");
            writer.write("return $T(size);", byteListLengthEncodedSize);
        }
    }

    private final class BigIntegerSizer extends Sizer {
        BigIntegerSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.pushState();
            writer.putContext("bigInteger", imp("java.math", "BigInteger"));
            writer.write("int size;");
            writer.write("if (${fieldName:L}.getClass() == byte[].class) {");
            writer.indent().write("size = ((byte[]) ${fieldName:L}).length;");
            writer.dedent().write("} else {");
            writer.indent().write("""
                byte[] bytes = ((${bigInteger:T}) ${fieldName:L}).toByteArray();
                this.${fieldName:L} = bytes;
                size = bytes.length;""");
            writer.dedent().write("}\n");
            writer.write("return $T(size);", byteListLengthEncodedSize);
            writer.popState();
        }
    }

    private final class BigDecimalSizer extends Sizer {
        BigDecimalSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.pushState();
            writer.putContext("bigDecimal", imp("java.math", "BigDecimal"));
            writer.write("""
                ${sparrowhawkBigDecimalHolder:T} holder;
                if (${fieldName:L}.getClass() == ${bigDecimal:T}.class) {
                    holder = new ${sparrowhawkBigDecimalHolder:T}((${bigDecimal:T}) ${fieldName:L});
                    ${fieldName:L} = holder;
                } else {
                    holder = (${sparrowhawkBigDecimalHolder:T}) ${fieldName:L};
                }
                return ${byteListLengthEncodedSize:T}(holder.size());""");
            writer.popState();
        }
    }

    private final class StructureListSizer extends Sizer {
        StructureListSizer(MemberShape field) {
            super(field);
        }

        @Override
        void generate0() {
            writer.write("""
                int len = ${fieldName:L}.size();
                int size = ${uintSize:T}(${encodeLenPrefixedListLength:T}(len));
                for (int i = 0; i < len; i++) {
                    size += ${byteListLengthEncodedSize:T}(${fieldName:L}.get(i).size());
                }
                return size;""");
        }
    }

    public Symbol listTarget(Shape shape) {
        return symbolProvider.toSymbol(shape).expectProperty("value", Symbol.class);
    }

    private void fixedSizeSizer(Stream<MemberShape> fields, int scale) {
        // TODO: this can be calculated as just `return scale * bitCount(fieldSet >> 3);`
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
                            writer.pushState();
                            var field = listFields.get(i);
                            var fieldName = fieldName(field);
                            writer.putContext("fieldName", fieldName);
                            if (isOptional(field)) {
                                writer.openBlock("if (has$L()) {", methodNameForField(field));
                            }

                            var fieldSymbol = symbolProvider.toSymbol(field);
                            var target = model.expectShape(field.getTarget());
                            if (isString(target) || isBigInteger(target)) {
                                writer.write("s.writeBytes(${fieldName:L});");
                            } else if (target.isBigDecimalShape()) {
                                writer.write("s.writeBigDecimal(${fieldName:L});");
                            } else if (target.isBlobShape()) {
                                writer.putContext(
                                    "writeBlob",
                                    target.hasTrait(SparrowhawkObjectTrait.class) ? "writeEncodedObject" : "writeBytes"
                                );
                                writer.write("s.${writeBlob:L}(${fieldName:L});");
                            } else if (target.isMapShape()) {
                                writer.write(
                                    "(($T) ${fieldName:L}).encodeTo(s);",
                                    fieldSymbol.expectProperty("sparrowhawkCollection", SymbolReference.class)
                                );
                            } else if (target.isListShape()) {
                                var valueType = listTarget(target);
                                var valueShape = valueType.expectProperty("shape", Shape.class);
                                if (isSparse(field)) {
                                    if (isStructure(valueShape)) {
                                        writer.write("s.writeSparseObjectList(${fieldName:L});");
                                    } else if (valueShape.isBlobShape()) {
                                        writer.write("s.writeSparseBlobList(${fieldName:L});");
                                    } else {
                                        writer.putContext("listImplType", fieldSymbol.expectProperty("listImplType"));
                                        writer.write("((${listImplType:T}) ${fieldName:L}).encodeTo(s);");
                                    }
                                } else if (isString(valueShape)) {
                                    writer.write("(($T) ${fieldName:L}).encodeTo(s);", StringList);
                                } else if (isVarintShape(valueShape) || isDoubleShape(valueShape) || valueShape
                                    .isFloatShape()) {
                                        writer.write("s.write$TList(${fieldName:L});", valueType);
                                    } else if (isStructure(valueShape)) {
                                        writer.write(
                                            "s.writeVarUL(encodeLenPrefixedListLength($L.size()));",
                                            fieldName
                                        );
                                        writer.write("for(int i = 0; i < ${fieldName:L}.size(); i++) {");
                                        writer.indent().write("${fieldName:L}.get(i).encodeTo(s);");
                                        writer.dedent().write("}");
                                    } else if (valueShape.isBlobShape()) {
                                        writer.write("s.writeBlobList(${fieldName:L});");
                                    } else {
                                        throw new RuntimeException("no list encoder for: " + field);
                                    }
                            } else if (isStructure(target)) {
                                writer.write("${fieldName:L}.encodeTo(s);");
                            } else {
                                throw new RuntimeException("unsupported list encoder: " + field);
                            }

                            if (isOptional(field)) {
                                writer.closeBlock("}");
                                if (i < listFields.size() - 1) {
                                    writer.write("");
                                }
                            }
                            writer.popState();
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
                                    m = "Date";
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
            generateMethod(
                "private void decodeVarintFieldSet($T d, int fieldSetIdx, long fieldSet)",
                CommonSymbols.SparrowhawkDeserializer,
                this::emitVarintDecodeMethod
            );
            int varintFieldSetCount = index.getVarintFieldSetCount(shape);
            for (int i = 0; i < varintFieldSetCount; i++) {
                writeVarintFieldsetDecode(i, index.getVarintMembers(shape, i));
            }
        }
        if (index.hasFourByteMembers(shape)) {
            generateMethod(
                "private void decodeFourByteFieldSet($T d, int fieldSetIdx, long fieldSet)",
                CommonSymbols.SparrowhawkDeserializer,
                this::emitFourByteDecodeMethod
            );
            int fourByteFieldSetCount = index.getFourByteFieldSetCount(shape);
            for (int i = 0; i < fourByteFieldSetCount; i++) {
                emitFourByteFieldSetDecoderMethod(i);
            }
        }
        if (index.hasEightByteMembers(shape)) {
            generateMethod(
                "private void decodeEightByteFieldSet($T d, int fieldSetIdx, long fieldSet)",
                CommonSymbols.SparrowhawkDeserializer,
                this::emitEightByteDecodeMethod
            );
            int eightByteFieldSetCount = index.getEightByteFieldSetCount(shape);
            for (int i = 0; i < eightByteFieldSetCount; i++) {
                emitEightByteFieldSetDecoderMethod(i);
            }
        }
        if (index.hasListMembers(shape)) {
            generateMethod(
                "private void decodeListFieldSet($T d, int fieldSetIdx, long fieldSet)",
                CommonSymbols.SparrowhawkDeserializer,
                this::emitListDecodeMethod
            );
            int listFieldSetCount = index.getListFieldSetCount(shape);
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
                default: d.skipAllVarints(fieldSet);
                    break;
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
        writer.pushState();
        writer.putContext("fieldSetIdx", fieldSetIdx);
        writer.openBlock(
            "private void decodeVarintFieldSet${fieldSetIdx:L}(${sparrowhawkDeserializer:T} d, long fieldSet) {",
            "}\n",
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
                if (varintMembers.size() < 61) {
                    writer.write("d.skipRemainingVarints(fieldSet, UNKNOWN_MASK_VARINT_${fieldSetIdx:L});");
                }
            }
        );
        writer.popState();
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
        writer.pushState();
        writer.putContext("decoderType", capitalize(width));
        writer.openBlock("switch (fieldSetIdx) {", """
                default: d.skipAll${decoderType:L}s(fieldSet);
                    break;
            }""", () -> {
            for (int i = 0; i < fieldSetCount; i++) {
                writer.putContext("idx", i);
                writer.openBlock(
                    "case ${idx:L}:",
                    "    break;",
                    () -> writer.write("decode${decoderType:L}ByteFieldSet${idx:L}(d, fieldSet);")
                );
            }
        });
        writer.popState();
    }

    private void emitFixedWidthFieldSetDecoder(
        String width,
        String method,
        int fieldSetIdx,
        List<MemberShape> fields
    ) {
        writer.pushState();
        writer.putContext("fieldSetIdx", fieldSetIdx);
        writer.putContext("decoderType", capitalize(width));
        writer.putContext("decoderCap", upperCase(width));
        writer.openBlock(
            "private void decode${decoderType:L}ByteFieldSet${fieldSetIdx:L}(${sparrowhawkDeserializer:T} d, long fieldSet) {",
            "}\n",
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
                        m = "date";
                    }
                    writer.write("this.$L = d.$L();", fieldName(field), m);
                    if (isOptional(field)) {
                        writer.closeBlock("}");
                    }
                }
                if (fields.size() < 61) {
                    writer.write(
                        "d.skipRemaining${decoderType:L}s(fieldSet, UNKNOWN_MASK_${decoderCap:L}_BYTE_${fieldSetIdx:L});"
                    );
                }
            }
        );
        writer.popState();
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
                default: d.skipAllLists(fieldSet);
                    break;
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
        writer.pushState();
        writer.putContext("fieldSetIdx", fieldSetIdx);
        writer.openBlock(
            "private void decodeListFieldSet${fieldSetIdx:L}(${sparrowhawkDeserializer:T} d, long fieldSet) {",
            "}\n",
            () -> { //TODO: check the sublist, not all lists
                if (index.hasRequiredLists(shape)) {
                    writer.write(
                        "$T.checkFields(fieldSet, REQUIRED_LIST_$L, \"lists\");",
                        CommonSymbols.SparrowhawkDeserializer,
                        fieldSetIdx
                    );
                }
                writer.write("this.$$list_$L = fieldSet;", fieldSetIdx);
                List<MemberShape> listMembers = index.getListMembers(shape, fieldSetIdx);
                for (var field : listMembers) {
                    writer.pushState();
                    if (isOptional(field)) {
                        writer.openBlock("if (has$L()) {", methodNameForField(field));
                    } else {
                        writer.openBlock("{");
                    }

                    var fieldSymbol = symbolProvider.toSymbol(field);
                    var fieldName = fieldName(field);
                    writer.putContext("fieldName", fieldName);
                    writer.putContext("fieldSymbol", fieldSymbol);

                    var shape = model.expectShape(field.getTarget());
                    if (shape.isBlobShape()) {
                        writer.putContext(
                            "blobMethod",
                            shape.hasTrait(SparrowhawkObjectTrait.class)
                                ? "object"
                                : settings.zeroCopyBuffers() ? "bytes" : "bytesCopied"
                        );
                        writer.write("this.${fieldName:L} = d.${blobMethod:L}();");
                    } else if (isString(shape)) {
                        writer.write("this.${fieldName:L} = d.string();");
                    } else if (isBigInteger(shape)) {
                        writer.write("this.${fieldName:L} = d.bigInteger();");
                    } else if (shape.isBigDecimalShape()) {
                        writer.write("this.${fieldName:L} = d.bigDecimal();");
                    } else if (shape.isMapShape()) {
                        setupSparrowhawkCollectionConstructor(fieldSymbol);
                        writer.write("""
                            ${sparrowhawkCollection:T} m = new ${sparrowhawkCollection:T}(${ctor:C});
                            m.decodeFrom(d);
                            this.${fieldName:L} = m;""");
                    } else if (shape.isListShape()) {
                        var valueSymbol = listTarget(model.expectShape(field.getTarget()));
                        var valueType = valueSymbol.expectProperty("shape", Shape.class);
                        writer.putContext("valueSymbol", valueSymbol);
                        if (isSparse(field)) {
                            if (isStructure(valueType)) {
                                writer.write("this.${fieldName:L} = d.decodeSparseObjectList(${valueSymbol:T}::new);");
                            } else if (valueType.isBlobShape()) {
                                writer.putContext("zeroCopy", settings.zeroCopyBuffers());
                                writer.write(
                                    "this.${fieldName:L} = d.decode${^zeroCopy}Copied${/zeroCopy}SparseBlobList();"
                                );
                            } else {
                                writer.putContext("listImplType", fieldSymbol.expectProperty("listImplType"));
                                writer.write("""
                                    ${listImplType:T} _l = new ${listImplType:T}();
                                    _l.decodeFrom(d);
                                    this.${fieldName:L} = _l;""");
                            }
                        } else if (isString(valueType)) {
                            writer.write("""
                                ${stringList:T} l = new ${stringList:T}();
                                l.decodeFrom(d);
                                this.${fieldName:L} = l;""");
                        } else if (isVarintShape(valueType) || valueType.isFloatShape() || isDoubleShape(valueType)) {
                            writer.write("this.${fieldName:L} = d.decode${valueSymbol:T}List();");
                        } else if (valueType.isBlobShape()) {
                            writer.putContext("zeroCopy", settings.zeroCopyBuffers());
                            writer.write(
                                "this.${fieldName:L} = d.decode${^zeroCopy}Copied${/zeroCopy}${byteBuffer:T}List();"
                            );
                        } else if (isStructure(valueType)) {
                            var arrayName = String.format("%sArr", fieldName);
                            writer.putContext("arrayName", arrayName);
                            writer.write("""
                                int ${fieldName:L}Len = ${decodeLenPrefixedListLengthChecked:T}(d.varUL());
                                ${valueSymbol:T}[] ${arrayName:L} = new ${valueSymbol:T}[${fieldName:L}Len];
                                for (int i = 0; i < ${fieldName:L}Len; i++) {
                                    ${valueSymbol:T} x = new ${valueSymbol:T}();
                                    x.decodeFrom(d);
                                    ${arrayName:L}[i] = x;
                                }
                                this.${fieldName:L} = ${asList:T}(${arrayName:L});""");
                        } else {
                            throw new RuntimeException("can't handle: " + field);
                        }
                    } else if (isStructure(shape)) {
                        writer.write("""
                            ${fieldSymbol:T} obj = new ${fieldSymbol:T}();
                            obj.decodeFrom(d);
                            this.${fieldName:L} = obj;""");
                    } else {
                        throw new RuntimeException("no decoder for: " + field);
                    }

                    writer.closeBlock("}");
                    writer.popState();
                }

                if (listMembers.size() < 61) {
                    writer.write("d.skipRemainingLists(fieldSet, UNKNOWN_MASK_LIST_${fieldSetIdx:L});");
                }
            }
        );
        writer.popState();
    }

    private static boolean isVarintShape(Shape type) {
        return switch (type.getType()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, INT_ENUM, LONG -> true;
            default -> false;
        };
    }

    private static boolean isDoubleShape(Shape type) {
        return type.getType() == DOUBLE || type.getType() == TIMESTAMP;
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

    private void generateTypeDecodeBranch(String methodName, SymbolReference fieldType, boolean writeElse) {
        writer.pushState();
        writer.putContext("fieldType", fieldType);
        writer.putContext("methodName", methodName);
        writer.putContext("writeElse", writeElse);
        writer.writeInline("""
            ${?writeElse}else ${/writeElse}if (type == ${fieldType:T}) {
                decode${methodName:L}FieldSet(d, fieldSetIdx, fieldSet);
            }
            """);
        writer.popState();
    }

    private void generateDecodeFrom() {
        writer.pushState();
        writer.openBlock("""
            int size = (int) ${decodeElementCount:T}(d.varUI());
            this.$$size = size;
            int start = d.pos();
            int end = start + size;

            while (d.pos()  < end) {""", "}", () -> {
            writer.write("""
                long fieldSet = d.varUL();
                int fieldSetIdx = ((fieldSet & 0b100) != 0) ? d.varUI() + 1 : 0;
                int type = (int) (fieldSet & 3);""");
            int emitted = 0;
            writer.putContext("decodeGenerator", writer.consumer(w -> {
            }));
            if (index.hasListMembers(shape)) {
                emitted++;
                generateTypeDecodeBranch("List", T_LIST, false);
            }
            if (index.hasVarintMembers(shape)) {
                generateTypeDecodeBranch("Varint", T_VARINT, emitted++ > 0);
            }
            if (index.hasFourByteMembers(shape)) {
                generateTypeDecodeBranch("FourByte", T_FOUR, emitted++ > 0);
            }
            if (index.hasEightByteMembers(shape)) {
                if (emitted++ == 3) {
                    writer.write("""
                        else {
                            decodeEightByteFieldSet(d, fieldSetIdx, fieldSet);
                        }""");
                } else {
                    generateTypeDecodeBranch("EightByte", T_EIGHT, emitted > 1);
                }
            }
            if (emitted != 4) {
                if (emitted > 0) {
                    writer.openBlock("else {");
                }
                writer.write("d.skipRemaining(fieldSet, type);");
                if (emitted > 0) {
                    writer.closeBlock("}");
                }
            }
        });
        writer.popState();
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

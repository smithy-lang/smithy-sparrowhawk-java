/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import static software.amazon.smithy.sparrowhawk.codegen.Util.isStructure;

import java.io.File;
import java.util.Map;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.ReservedWordSymbolProvider;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.java.sparrowhawk.BooleanMap;
import software.amazon.smithy.java.sparrowhawk.ByteMap;
import software.amazon.smithy.java.sparrowhawk.BytesMap;
import software.amazon.smithy.java.sparrowhawk.CopiedBytesMap;
import software.amazon.smithy.java.sparrowhawk.DoubleMap;
import software.amazon.smithy.java.sparrowhawk.FloatMap;
import software.amazon.smithy.java.sparrowhawk.IntegerMap;
import software.amazon.smithy.java.sparrowhawk.LongMap;
import software.amazon.smithy.java.sparrowhawk.ShortMap;
import software.amazon.smithy.java.sparrowhawk.SparseStructureMap;
import software.amazon.smithy.java.sparrowhawk.StringMap;
import software.amazon.smithy.java.sparrowhawk.StructureMap;
import software.amazon.smithy.java.sparrowhawk.TimestampMap;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;
import software.amazon.smithy.utils.StringUtils;

public class SparrowhawkSymbolVisitor implements SymbolProvider, ShapeVisitor<Symbol> {
    private final Model model;
    private final ReservedWordSymbolProvider.Escaper escaper;
    private final ServiceShape service;
    private final SparrowhawkSettings settings;
    private final NullableIndex nullableIndex;
    private final Map<ShapeId, ShapeId> collectionRepresentatives;

    public SparrowhawkSymbolVisitor(Model model, ServiceShape service, SparrowhawkSettings settings) {
        this.model = model;
        this.service = service;
        this.settings = settings;

        // Load reserved words from new-line delimited files.
        var reservedWords = new ReservedWordsBuilder()
            .loadWords(SparrowhawkSymbolVisitor.class.getResource("reserved-words.txt"), this::escapeWord)
            .build();

        escaper = ReservedWordSymbolProvider.builder()
            .nameReservedWords(reservedWords)
            .memberReservedWords(reservedWords)
            // Only escape words when the symbol has a definition file to
            // prevent escaping intentional references to built-in types.
            .escapePredicate((shape, symbol) -> !StringUtils.isEmpty(symbol.getDefinitionFile()))
            .buildEscaper();

        this.nullableIndex = NullableIndex.of(model);
        this.collectionRepresentatives = CollectionSupport.analyze(model, service);
    }

    protected final Model model() {
        return model;
    }

    protected final ReservedWordSymbolProvider.Escaper escaper() {
        return escaper;
    }

    protected final ServiceShape service() {
        return service;
    }

    protected final SparrowhawkSettings settings() {
        return settings;
    }

    private String escapeWord(String word) {
        return "_" + word;
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        Symbol symbol = shape.accept(this);
        return escaper.escapeSymbol(shape, symbol);
    }

    @Override
    public String toMemberName(MemberShape shape) {
        return escaper.escapeMemberName(StringUtils.uncapitalize(shape.getMemberName()));
    }

    @Override
    public Symbol blobShape(BlobShape shape) {
        return createSymbolBuilder(shape, "ByteBuffer", "java.nio").build();
    }

    @Override
    public Symbol booleanShape(BooleanShape shape) {
        return createSymbolBuilder(shape, "Boolean").build();
    }

    @Override
    public Symbol listShape(ListShape shape) {
        Shape memberShape = model.expectShape(shape.getMember().getTarget());
        var valueSymbol = toSymbol(memberShape);
        Symbol.Builder b = listSymbolBuilder(shape);
        b.putProperty("value", valueSymbol).addReference(valueSymbol);
        if (CollectionSupport.needsFlyweight(model, shape)) {
            Shape representative = collectionRepresentative(shape);
            var flyweight = collectionFlyweightSymbol(representative);
            var flyweightRef = flyweight.toReference(null);
            b.putProperty("simple", false);
            b.putProperty("sparrowhawkField", Symbol.builder().name("Object").build());
            b.putProperty("listImplType", flyweightRef);
            b.putProperty("generatedCollection", flyweight);
            b.putProperty("generatedCollectionOwner", representative.getId());
            b.addReference(flyweightRef);
            return b.build();
        }
        boolean nullable = nullableIndex.isMemberNullable(shape.getMember());
        if (nullable || memberShape.isStringShape()) {
            var target = model.expectShape(shape.getMember().getTarget());
            b.putProperty("simple", switch (target.getType()) {
                case BOOLEAN, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE, TIMESTAMP -> true;
                default -> false;
            });
            if (!nullable) {
                b.putProperty("sparrowhawkField", Symbol.builder().name("Object").build());
                b.putProperty("listImplType", CommonSymbols.StringList);
            } else if (!isStructure(memberShape) && !memberShape.isBlobShape()) {
                b.putProperty("sparrowhawkField", Symbol.builder().name("Object").build());
                b.putProperty("listImplType", switch (memberShape.getType()) {
                    case STRING, ENUM -> CommonSymbols.SparseStringList;
                    case BOOLEAN -> CommonSymbols.SparseBooleanList;
                    case BYTE -> CommonSymbols.SparseByteList;
                    case SHORT -> CommonSymbols.SparseShortList;
                    case INTEGER, INT_ENUM -> CommonSymbols.SparseIntegerList;
                    case LONG -> CommonSymbols.SparseLongList;
                    case FLOAT -> CommonSymbols.SparseFloatList;
                    case DOUBLE -> CommonSymbols.SparseDoubleList;
                    case TIMESTAMP -> CommonSymbols.SparseTimestampList;
                    default -> throw new IllegalStateException("rejected by validation: " + shape);
                });
            }
        }
        return b.build();
    }

    private Symbol collectionFlyweightSymbol(Shape shape) {
        String suffix;
        if (shape.isMapShape()) {
            suffix = "$Map";
        } else if (shape.hasTrait(UniqueItemsTrait.class)) {
            suffix = "$Set";
        } else {
            suffix = "$List";
        }
        String name = getDefaultShapeName(shape) + suffix;
        String namespace = shape.getId().getNamespace();
        return Symbol.builder()
            .name(name)
            .namespace(namespace, ".")
            .definitionFile(namespace.replaceAll("\\.", File.separator) + File.separator + name + ".java")
            .build();
    }

    protected Symbol.Builder listSymbolBuilder(ListShape shape) {
        if (shape.hasTrait(UniqueItemsTrait.class)) {
            return createSymbolBuilder(shape, "Set", "java.util");
        } else {
            return createSymbolBuilder(shape, "List", "java.util");
        }
    }

    @Override
    public Symbol mapShape(MapShape shape) {
        var keySymbol = toSymbol(model.expectShape(shape.getKey().getTarget()));
        var valueShape = model.expectShape(shape.getValue().getTarget());
        var valueSymbol = toSymbol(valueShape);
        var mapSymbol = mapSymbolBuilder(shape)
            .putProperty("key", keySymbol)
            .putProperty("value", valueSymbol)
            .putProperty("sparrowhawkField", Symbol.builder().name("Object").build())
            .addReference(keySymbol)
            .addReference(valueSymbol);

        if (CollectionSupport.needsFlyweight(model, shape)) {
            Shape representative = collectionRepresentative(shape);
            var flyweight = collectionFlyweightSymbol(representative);
            var flyweightRef = flyweight.toReference(null);
            mapSymbol.putProperty("sparrowhawkCollection", flyweightRef)
                .putProperty("generatedCollection", flyweight)
                .putProperty("generatedCollectionOwner", representative.getId())
                .addReference(flyweightRef);
            return mapSymbol.build();
        }

        var sparrowhawkCollectionImpl = switch (valueShape.getType()) {
            case BOOLEAN -> BooleanMap.class;
            case STRING, ENUM -> StringMap.class;
            case BYTE -> ByteMap.class;
            case SHORT -> ShortMap.class;
            case INTEGER, INT_ENUM -> IntegerMap.class;
            case LONG -> LongMap.class;
            case FLOAT -> FloatMap.class;
            case DOUBLE -> DoubleMap.class;
            case STRUCTURE, UNION -> sparseMap(shape) ? SparseStructureMap.class : StructureMap.class;
            case BLOB -> settings.zeroCopyBuffers() ? BytesMap.class : CopiedBytesMap.class;
            case TIMESTAMP -> TimestampMap.class;
            default -> throw new IllegalStateException("rejected by validation: " + valueShape.getType());
        };

        var sparrowhawkCollectionSymbol = sparrowhawkCollectionSymbol(sparrowhawkCollectionImpl);
        mapSymbol.putProperty("sparrowhawkCollection", sparrowhawkCollectionSymbol)
            .addReference(sparrowhawkCollectionSymbol);
        return mapSymbol.build();
    }

    private Shape collectionRepresentative(Shape shape) {
        ShapeId representative = collectionRepresentatives.getOrDefault(shape.getId(), shape.getId());
        return model.expectShape(representative);
    }

    private static SymbolReference sparrowhawkCollectionSymbol(Class<?> sparrowhawkCollectionImpl) {
        return Symbol.builder()
            .namespace(sparrowhawkCollectionImpl.getPackageName(), ".")
            .name(sparrowhawkCollectionImpl.getSimpleName())
            .build()
            .toReference(null);
    }

    private static boolean sparseMap(MapShape mapShape) {
        return mapShape.hasTrait(SparseTrait.class);
    }

    protected Symbol.Builder mapSymbolBuilder(MapShape shape) {
        return createSymbolBuilder(shape, "Map", "java.util");
    }

    @Override
    public Symbol byteShape(ByteShape shape) {
        return createSymbolBuilder(shape, "Byte").build();
    }

    @Override
    public Symbol shortShape(ShortShape shape) {
        return createSymbolBuilder(shape, "Short").build();
    }

    @Override
    public Symbol integerShape(IntegerShape shape) {
        return createSymbolBuilder(shape, "Integer").build();
    }

    @Override
    public Symbol longShape(LongShape shape) {
        return createSymbolBuilder(shape, "Long").build();
    }

    @Override
    public Symbol floatShape(FloatShape shape) {
        return createSymbolBuilder(shape, "Float").build();
    }

    @Override
    public Symbol documentShape(DocumentShape shape) {
        throw new IllegalArgumentException("Documents are not currently supported");
    }

    @Override
    public Symbol doubleShape(DoubleShape shape) {
        return createSymbolBuilder(shape, "Double").build();
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape shape) {
        return createSymbolBuilder(shape, "BigInteger", "java.math")
            .putProperty("sparrowhawkField", Symbol.builder().name("Object").build())
            .build();
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape shape) {
        return createSymbolBuilder(shape, "BigDecimal", "java.math")
            .putProperty("sparrowhawkField", Symbol.builder().name("Object").build())
            .build();
    }

    @Override
    public Symbol operationShape(OperationShape shape) {
        return createSymbolBuilder(shape, shape.getId().getName(), shape.getId().getNamespace()).build();
    }

    @Override
    public Symbol resourceShape(ResourceShape shape) {
        return createSymbolBuilder(shape, shape.getId().getName(), shape.getId().getNamespace()).build();
    }

    @Override
    public Symbol serviceShape(ServiceShape shape) {
        return createSymbolBuilder(shape, shape.getId().getName(), shape.getId().getNamespace()).build();
    }

    @Override
    public Symbol stringShape(StringShape shape) {
        return createSymbolBuilder(shape, "String")
            .putProperty("sparrowhawkField", Symbol.builder().name("Object").build())
            .build();
    }

    protected String getDefaultShapeName(Shape shape) {
        // Use the service-aliased name
        return settings.getClassPrefix() + StringUtils.capitalize(shape.getId().getName(service));
    }

    @Override
    public Symbol structureShape(StructureShape shape) {
        Symbol base = structureSymbolBuilder(shape).build();
        return base.toBuilder()
            .definitionFile(
                base.getNamespace().replaceAll("\\.", File.separator)
                    + File.separator + base.getName() + ".java"
            )
            .build();
    }

    protected Symbol.Builder structureSymbolBuilder(StructureShape shape) {
        return createSymbolBuilder(shape, getDefaultShapeName(shape), shape.getId().getNamespace());
    }

    @Override
    public Symbol unionShape(UnionShape shape) {
        Symbol base = unionSymbolBuilder(shape).build();
        return base.toBuilder()
            .definitionFile(
                base.getNamespace().replaceAll("\\.", File.separator)
                    + File.separator + base.getName() + ".java"
            )
            .build();
    }

    protected Symbol.Builder unionSymbolBuilder(UnionShape shape) {
        return createSymbolBuilder(shape, getDefaultShapeName(shape), shape.getId().getNamespace());
    }

    @Override
    public Symbol memberShape(MemberShape shape) {
        Shape targetShape = model.getShape(shape.getTarget())
            .orElseThrow(() -> new CodegenException("Shape not found: " + shape.getTarget()));

        Symbol targetSymbol = null;
        if (targetShape.getType().getCategory() == ShapeType.Category.SIMPLE) {
            targetSymbol = primitive(targetShape);
        }

        if (targetSymbol == null) {
            targetSymbol = toSymbol(targetShape);
        }
        return targetSymbol.toBuilder()
            .putProperty("memberShape", shape)
            .putProperty("methodName", StringUtils.capitalize(shape.getMemberName()))
            .build();
    }

    private Symbol primitive(Shape targetShape) {
        switch (targetShape.getType()) {
            case BOOLEAN -> {
                return createSymbolBuilder(targetShape, "boolean")
                    .putProperty("boxed", booleanShape((BooleanShape) targetShape))
                    .build();
            }
            case BYTE -> {
                return createSymbolBuilder(targetShape, "byte")
                    .putProperty("boxed", byteShape((ByteShape) targetShape))
                    .build();
            }
            case SHORT -> {
                return createSymbolBuilder(targetShape, "short")
                    .putProperty("boxed", shortShape((ShortShape) targetShape))
                    .build();
            }
            case INTEGER -> {
                return createSymbolBuilder(targetShape, "int")
                    .putProperty("boxed", integerShape((IntegerShape) targetShape))
                    .build();
            }
            case INT_ENUM -> {
                return createSymbolBuilder(targetShape, "int")
                    .putProperty("boxed", intEnumShape((IntEnumShape) targetShape))
                    .build();
            }
            case LONG -> {
                return createSymbolBuilder(targetShape, "long")
                    .putProperty("boxed", longShape((LongShape) targetShape))
                    .build();
            }
            case FLOAT -> {
                return createSymbolBuilder(targetShape, "float")
                    .putProperty("boxed", floatShape((FloatShape) targetShape))
                    .build();
            }
            case DOUBLE -> {
                return createSymbolBuilder(targetShape, "double")
                    .putProperty("boxed", doubleShape((DoubleShape) targetShape))
                    .build();
            }
        }
        return null;
    }

    @Override
    public Symbol timestampShape(TimestampShape shape) {
        return createSymbolBuilder(shape, "Date", "java.util").build();
    }

    protected Symbol.Builder createSymbolBuilder(Shape shape, String typeName) {
        return Symbol.builder().putProperty("shape", shape).name(typeName);
    }

    protected Symbol.Builder createSymbolBuilder(Shape shape, String typeName, String namespace) {
        return createSymbolBuilder(shape, typeName).namespace(namespace, ".");
    }
}

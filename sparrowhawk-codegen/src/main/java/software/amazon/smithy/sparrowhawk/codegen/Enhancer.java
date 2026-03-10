/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import software.amazon.smithy.java.sparrowhawk.KConstants;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.protocol.traits.IdxTrait;

/**
 * A reflective Sparrowhawk payload printer for debugging purposes. Uses a Smithy model to know how to decode
 * Sparrowhawk payloads (instead of seeing them as a bunch of byte lists).
 */
@SuppressWarnings("unused")
public final class Enhancer {

    private final Model model;
    private final PrintStream output;
    private final Map<ShapeId, Map<Integer, Map<Integer, MemberShape>>> memberCache = new ConcurrentHashMap<>();

    /**
     * @param model the Smithy model for the payloads this Enhancer can decode
     */
    public Enhancer(Model model) {
        this(model, System.err);
    }

    /**
     * @param model the Smithy model for the payloads this Enhancer can decode
     * @param output a PrintStream to write the decoded payload to
     */
    public Enhancer(Model model, PrintStream output) {
        this.model = model;
        this.output = output;
    }

    /**
     * Dumps a Sparrowhawk-serialized Smithy shape in human-readable format.
     *
     * @param payload a raw Sparrowhawk payload
     * @param shape the shape used to decode this payload. Can be of any type
     */
    public void enhance(byte[] payload, Shape shape) {
        try {
            enhanceNext(new SparrowhawkDeserializer(payload), shape, 0);
        } catch (BadInputException e) {
            printWithIndent(0, "[ERROR (idx: %d)] %s", e.pos, e.getMessage());
        }
    }

    private void enhanceNext(SparrowhawkDeserializer deser, Shape shape, int level) {
        int startPos = deser.pos();
        if (shape instanceof StructureShape) {
            int type = deser.varUI();
            int afterType = deser.pos();
            if ((type & 1) != 0) {
                throw new BadInputException(deser.pos(), "Invalid struct (encountered type %d)", type & 7);
            }
            int structLen = type >> 1;
            if (structLen == 0) {
                printWithIndent(
                    level,
                    "[Struct (size: %d, type: %s, bytes: %d-%d)][/Struct]",
                    structLen,
                    shape.getId(),
                    startPos,
                    afterType - 1
                );
                return;
            }
            printWithIndent(
                level,
                "[Struct (size: %d, type: %s, bytes: %d-%d)]",
                structLen,
                shape.getId(),
                startPos,
                afterType + structLen - 1
            );
            enhanceStruct(deser, (StructureShape) shape, level + 1, structLen);
            printWithIndent(level, "[/Struct]");
        } else if (shape instanceof ListShape) {
            long rawLen = deser.varUL();
            int afterLen = deser.pos();
            long len = KConstants.decodeElementCount(rawLen);
            Shape member = model.expectShape(((ListShape) shape).getMember().getTarget());
            if (len == 0) {
                printWithIndent(
                    level,
                    "[List (size: %d, valueType: %s, bytes: %d-%d)][/List]",
                    len,
                    member.getId(),
                    startPos,
                    afterLen - 1
                );
                return;
            }
            printWithIndent(level, "[List (size: %d, valueType: %s, bytes: %d-...)]", len, member.getId(), startPos);
            for (int i = 0; i < len; i++) {
                enhanceNext(deser, member, level + 1);
            }
            printWithIndent(level, "[/List (bytes: %d-%d)]", startPos, deser.pos() - 1);
        } else if (shape instanceof MapShape) {
            Shape valueShape = model.expectShape(((MapShape) shape).getValue().getTarget());

            int type = deser.varUI();
            int afterType = deser.pos();
            if ((type & 1) != 0) {
                throw new BadInputException(deser.pos(), "Invalid map (encountered type %d)", type & 7);
            }
            int structLen = type >> 1;
            if (structLen == 0) {
                printWithIndent(
                    level,
                    "[Map (size: %d, keys: %d, valueType: %s, bytes: %d-%d)][/Map]",
                    structLen,
                    0,
                    valueShape.getId(),
                    startPos,
                    afterType - 1
                );
                return;
            }

            int beforeTsh = deser.pos();
            long typeSectionHeader = deser.varUL();
            int afterTsh = deser.pos();
            int fieldType = (int) (typeSectionHeader & 3);
            if (fieldType != KConstants.T_LIST) {
                throw new BadInputException(
                    deser.pos(),
                    "Structure field type MUST be %d, was %d",
                    KConstants.T_LIST,
                    fieldType
                );
            }
            if ((typeSectionHeader & 4) != 0) {
                throw new BadInputException(deser.pos(), "Maps should not have continuation bit set");
            }

            var fieldsSet = new HashSet<Integer>();
            for (int i = 0; i < 61; i++) {
                if ((typeSectionHeader >> (3 + i) & 1) == 1) {
                    fieldsSet.add(i);
                }
            }

            if (!fieldsSet.equals(Set.of(0, 1))) {
                throw new BadInputException(
                    deser.pos(),
                    "Non-empty maps must have fields 0 and 1 set, found %s",
                    fieldsSet
                );
            }

            int beforeKeyLen = deser.pos();
            long rawKeyLen = deser.varUL();
            int afterKeyLen = deser.pos();
            int keyLen = (int) KConstants.decodeElementCount(rawKeyLen);
            List<String> keys = new ArrayList<>();
            int[] keyStartPos = new int[keyLen];
            int[] keyEndPos = new int[keyLen];
            for (int i = 0; i < keyLen; i++) {
                keyStartPos[i] = deser.pos();
                keys.add(deser.string());
                keyEndPos[i] = deser.pos() - 1;
            }
            int beforeValLen = deser.pos();
            long rawValLen = deser.varUL();
            int afterValLen = deser.pos();
            int valueLen = (int) KConstants.decodeElementCount(rawValLen);
            if (keyLen != valueLen) {
                throw new BadInputException(
                    deser.pos(),
                    "Maps must have key and value length lists of same size. " +
                        "Keys length: %d; Values length: %d",
                    keyLen,
                    valueLen
                );
            }
            printWithIndent(
                level,
                "[Map (size: %d, keys: %d, valueType: %s, bytes: %d-..., typeBytes: %d-%d, tshBytes: %d-%d, keyLenBytes: %d-%d, valLenBytes: %d-%d)]",
                structLen,
                keyLen,
                valueShape.getId(),
                startPos,
                startPos,
                afterType - 1,
                beforeTsh,
                afterTsh - 1,
                beforeKeyLen,
                afterKeyLen - 1,
                beforeValLen,
                afterValLen - 1
            );
            for (int i = 0; i < keyLen; i++) {
                if (valueShape.getType().getCategory() == ShapeType.Category.SIMPLE) {
                    int beforeVal = deser.pos();
                    Object val = getSimpleValue(deser, valueShape);
                    int afterVal = deser.pos();
                    printWithIndent(
                        level + 1,
                        "[Entry (key: %s, keyBytes: %d-%d, valueBytes: %d-%d)]%s[/Entry]",
                        keys.get(i),
                        keyStartPos[i],
                        keyEndPos[i],
                        beforeVal,
                        afterVal - 1,
                        val
                    );
                } else {
                    printWithIndent(
                        level + 1,
                        "[Entry (key: %s, keyBytes: %d-%d)]",
                        keys.get(i),
                        keyStartPos[i],
                        keyEndPos[i]
                    );
                    enhanceNext(deser, valueShape, level + 2);
                    printWithIndent(level + 1, "[/Entry]", keys.get(i));
                }
            }
            printWithIndent(level, "[/Map (bytes: %d-%d)]", startPos, deser.pos() - 1);
        } else if (shape.getType().getCategory() == ShapeType.Category.SIMPLE) {
            Object val = getSimpleValue(deser, shape);
            int afterVal = deser.pos();
            printWithIndent(level, "[> %s (bytes: %d-%d)]", val, startPos, afterVal - 1);
        } else {
            throw new UnsupportedOperationException(shape.toString());
        }
    }

    private void printWithIndent(int level, String s, Object... args) {
        for (int i = 0; i < level; i++) { output.print("    "); }
        output.printf((s) + "%n", args);
    }

    private void enhanceStruct(SparrowhawkDeserializer deser, StructureShape shape, int level, long len) {
        long end = deser.pos() + len;
        while (deser.pos() < end) {
            int beforeTsh = deser.pos();
            long typeSectionHeader = deser.varUL();
            int afterTsh = deser.pos();
            int fieldType = (int) (typeSectionHeader & 3);

            int offset = 0;
            int beforeOffset = -1, afterOffset = -1;
            if ((typeSectionHeader & 4) != 0) {
                beforeOffset = deser.pos();
                offset = deser.varUI() + 1;
                afterOffset = deser.pos();
            }

            var fieldsSet = new LinkedHashSet<Integer>();
            for (int i = 0; i < 61; i++) {
                if ((typeSectionHeader >> (3 + i) & 1) == 1) {
                    fieldsSet.add(i + (offset * 61));
                }
            }

            if (beforeOffset >= 0) {
                printWithIndent(
                    level,
                    "[TypeSection (type: %s, fields: %s, headerBytes: %d-%d, offsetBytes: %d-%d)]",
                    typeSectionTypeToString(deser, fieldType),
                    fieldsSet,
                    beforeTsh,
                    afterTsh - 1,
                    beforeOffset,
                    afterOffset - 1
                );
            } else {
                printWithIndent(
                    level,
                    "[TypeSection (type: %s, fields: %s, headerBytes: %d-%d)]",
                    typeSectionTypeToString(deser, fieldType),
                    fieldsSet,
                    beforeTsh,
                    afterTsh - 1
                );
            }

            for (Integer field : fieldsSet) {
                MemberShape member = getSmithyMember(shape, fieldType, field);
                Shape target = model.expectShape(member.getTarget());
                if (target.getType().getCategory() == ShapeType.Category.SIMPLE) {
                    int beforeVal = deser.pos();
                    Object val = getSimpleValue(deser, target);
                    int afterVal = deser.pos();
                    printWithIndent(
                        level + 1,
                        "[Member (name: %s, kIdx: %d, sIdx: %d, type: %s, bytes: %d-%d)]%s[/Member]",
                        member.getMemberName(),
                        field,
                        member.expectTrait(IdxTrait.class).getValue(),
                        target.getId(),
                        beforeVal,
                        afterVal - 1,
                        val
                    );
                } else {
                    printWithIndent(
                        level + 1,
                        "[Member (name: %s, kIdx: %d, sIdx: %d, type: %s)]",
                        member.getMemberName(),
                        field,
                        member.expectTrait(IdxTrait.class).getValue(),
                        target.getId()
                    );
                    enhanceNext(deser, target, level + 2);
                    printWithIndent(level + 1, "[/Member]");
                }
            }
            printWithIndent(level, "[/TypeSection]");
        }
    }

    private static byte[] array(ByteBuffer buffer) {
        byte[] array = new byte[buffer.remaining()];
        buffer.get(array);
        return array;
    }

    private Object getSimpleValue(SparrowhawkDeserializer deser, Shape target) {
        switch (target.getType()) {
            case BLOB -> {
                return HexFormat.ofDelimiter(":").formatHex(array(deser.bytes()));
            }
            case BOOLEAN -> {
                return deser.bool();
            }
            case STRING, ENUM -> {
                return deser.string();
            }
            case BIG_INTEGER -> {
                return deser.bigInteger();
            }
            case BIG_DECIMAL -> {
                return deser.bigDecimal();
            }
            case TIMESTAMP -> {
                return Instant.ofEpochMilli((long) (deser.d8() * 1000L)).toString();
            }
            case BYTE -> {
                return (byte) deser.varI();
            }
            case SHORT -> {
                return (short) deser.varI();
            }
            case INTEGER, INT_ENUM -> {
                return deser.varI();
            }
            case LONG -> {
                return deser.varL();
            }
            case FLOAT -> {
                return deser.f4();
            }
            case DOUBLE -> {
                return deser.d8();
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private MemberShape getSmithyMember(StructureShape shape, int fieldType, Integer field) {
        Map<Integer, Map<Integer, MemberShape>> structureMap = memberCache.computeIfAbsent(
            shape.getId(),
            (unused) -> populateMemberCache(shape)
        );

        return structureMap.getOrDefault(fieldType, Collections.emptyMap()).get(field);
    }

    private Map<Integer, Map<Integer, MemberShape>> populateMemberCache(StructureShape shape) {
        final Map<Integer, List<MemberShape>> byType = shape.members()
            .stream()
            .collect(Collectors.groupingBy((ms) -> {
                switch (model.expectShape(ms.getTarget()).getType()) {
                    case BLOB, STRING, DOCUMENT, ENUM, LIST, SET, MAP, STRUCTURE, UNION, BIG_INTEGER, BIG_DECIMAL -> {
                        return KConstants.T_LIST;
                    }
                    case BOOLEAN, BYTE, SHORT, INTEGER, LONG, INT_ENUM -> {
                        return KConstants.T_VARINT;
                    }
                    case TIMESTAMP, DOUBLE -> {
                        return KConstants.T_EIGHT;
                    }
                    case FLOAT -> {
                        return KConstants.T_FOUR;
                    }
                    default -> throw new UnsupportedOperationException();
                }
            }));

        final Map<Integer, Map<Integer, MemberShape>> retVal = new HashMap<>();
        for (Map.Entry<Integer, List<MemberShape>> e : byType.entrySet()) {
            List<MemberShape> ordered = new ArrayList<>(e.getValue());
            ordered.sort(Comparator.comparingInt(ms -> ms.expectTrait(IdxTrait.class).getValue()));
            final Map<Integer, MemberShape> newIdxed = new HashMap<>();
            for (int i = 0; i < ordered.size(); i++) {
                newIdxed.put(i, ordered.get(i));
            }
            retVal.put(e.getKey(), Collections.unmodifiableMap(newIdxed));
        }
        return Collections.unmodifiableMap(retVal);
    }

    private String typeSectionTypeToString(SparrowhawkDeserializer deser, int i) {
        return switch (i) {
            case 0 -> "lists";
            case 1 -> "varints";
            case 2 -> "four-byte items";
            case 3 -> "eight-byte items";
            default -> throw new BadInputException(deser.pos(), "Unknown type %d", i);
        };
    }

    private static final class BadInputException extends RuntimeException {
        private final int pos;

        public BadInputException(String message, int pos) {
            super(message);
            this.pos = pos;
        }

        public BadInputException(String message, Throwable cause, int pos) {
            super(message, cause);
            this.pos = pos;
        }

        public BadInputException(int pos, String message, Object... args) {
            this(java.lang.String.format(message, args), pos);
        }
    }

}

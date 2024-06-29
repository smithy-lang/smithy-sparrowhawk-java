/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import java.util.*;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.KnowledgeIndex;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.StreamingTrait;

public final class SparrowhawkIndex implements KnowledgeIndex {
    private final Map<ShapeId, Map<Integer, List<MemberShape>>> varintMembers = new HashMap<>();
    private final Map<ShapeId, Map<Integer, List<MemberShape>>> fourByteMembers = new HashMap<>();
    private final Map<ShapeId, Map<Integer, List<MemberShape>>> eightByteMembers = new HashMap<>();
    private final Map<ShapeId, Map<Integer, List<MemberShape>>> listMembers = new HashMap<>();

    public SparrowhawkIndex(Model model) {
        for (StructureShape structureShape : model.getStructureShapes()) {
            index(structureShape, model);
        }
        for (UnionShape unionShape : model.getUnionShapes()) {
            index(unionShape, model);
        }
    }

    public static SparrowhawkIndex of(Model model) {
        return model.getKnowledge(SparrowhawkIndex.class, SparrowhawkIndex::new);
    }

    private void index(Shape shape, Model model) {
        List<MemberShape> members = new ArrayList<>(shape.members());
        if (members.isEmpty() || !members.stream().findFirst().get().hasTrait(SparrowhawkFieldTrait.class)) {
            return;
        }

        members.sort(
            Comparator.comparing((MemberShape m) -> m.expectTrait(SparrowhawkFieldTrait.class).getType())
                .thenComparing(m -> m.expectTrait(SparrowhawkFieldTrait.class).getFieldSetIdx())
                .thenComparing(m -> m.expectTrait(SparrowhawkFieldTrait.class).getTypeIdx())
        );

        for (MemberShape ms : members) {
            if (model.expectShape(ms.getTarget()).hasTrait(StreamingTrait.class)) {
                continue;
            }

            SparrowhawkFieldTrait trait = ms.expectTrait(SparrowhawkFieldTrait.class);
            (switch (trait.getType()) {
                case VARINT -> varintMembers;
                case LIST -> listMembers;
                case FOUR_BYTE -> fourByteMembers;
                case EIGHT_BYTE -> eightByteMembers;
            }).computeIfAbsent(shape.toShapeId(), $ -> new HashMap<>())
                .computeIfAbsent(trait.getFieldSetIdx(), $ -> new ArrayList<>())
                .add(ms);
        }
    }

    public int getVarintFieldSetCount(ToShapeId shape) {
        return varintMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).size();
    }

    public boolean hasVarintMembers(ToShapeId shape) {
        return !varintMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).isEmpty();
    }

    public List<MemberShape> getVarintMembers(ToShapeId shape, int fieldSetIdx) {
        return Collections.unmodifiableList(
            varintMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap())
                .getOrDefault(fieldSetIdx, Collections.emptyList())
        );
    }

    public int getFourByteFieldSetCount(ToShapeId shape) {
        return fourByteMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).size();
    }

    public boolean hasFourByteMembers(ToShapeId shape) {
        return !fourByteMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).isEmpty();
    }

    public List<MemberShape> getFourByteMembers(ToShapeId shape, int fieldSetIdx) {
        return Collections.unmodifiableList(
            fourByteMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap())
                .getOrDefault(fieldSetIdx, Collections.emptyList())
        );
    }

    public int getEightByteFieldSetCount(ToShapeId shape) {
        return eightByteMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).size();
    }

    public boolean hasEightByteMembers(ToShapeId shape) {
        return !eightByteMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).isEmpty();
    }

    public List<MemberShape> getEightByteMembers(ToShapeId shape, int fieldSetIdx) {
        return Collections.unmodifiableList(
            eightByteMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap())
                .getOrDefault(fieldSetIdx, Collections.emptyList())
        );
    }

    public int getListFieldSetCount(ToShapeId shape) {
        return listMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).size();
    }

    public boolean hasListMembers(ToShapeId shape) {
        return !listMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap()).isEmpty();
    }

    public boolean hasRequiredLists(ToShapeId shape) {
        return listMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap())
            .values()
            .stream()
            .flatMap(Collection::stream)
            .anyMatch(ms -> ms.expectTrait(SparrowhawkFieldTrait.class).isRequired());
    }

    public List<MemberShape> getListMembers(ToShapeId shape, int fieldSetIdx) {
        return Collections.unmodifiableList(
            listMembers.getOrDefault(shape.toShapeId(), Collections.emptyMap())
                .getOrDefault(fieldSetIdx, Collections.emptyList())
        );
    }
}

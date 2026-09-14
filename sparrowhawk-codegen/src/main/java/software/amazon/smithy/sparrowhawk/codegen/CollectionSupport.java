/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

final class CollectionSupport {
    private static final Logger LOGGER = Logger.getLogger(CollectionSupport.class.getName());

    private CollectionSupport() {}

    static boolean isCollection(Shape shape) {
        return shape.isListShape() || shape.isMapShape();
    }

    static boolean needsFlyweight(Model model, Shape shape) {
        if (shape instanceof ListShape l) {
            Shape member = model.expectShape(l.getMember().getTarget());
            return shape.hasTrait(UniqueItemsTrait.class)
                || isCollection(member)
                || needsLeafCodec(member);
        }
        if (shape instanceof MapShape m) {
            Shape value = model.expectShape(m.getValue().getTarget());
            return isCollection(value) || needsLeafCodec(value);
        }
        return false;
    }

    private static boolean needsLeafCodec(Shape shape) {
        return switch (shape.getType()) {
            case BIG_INTEGER, BIG_DECIMAL -> true;
            default -> false;
        };
    }

    static Map<ShapeId, ShapeId> analyze(Model model, ServiceShape service) {
        Map<ShapeId, String> signatures = new HashMap<>();
        Map<String, ShapeId> representativeBySignature = new HashMap<>();
        List<Shape> collections = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        new Walker(model).iterateShapes(service).forEachRemaining(shape -> {
            if (shape instanceof ListShape list) {
                validateList(model, list, errors);
            } else if (shape instanceof MapShape map) {
                validateMap(model, map, errors);
            }
            if (isCollection(shape) && needsFlyweight(model, shape)) {
                collections.add(shape);
            }
        });

        if (!errors.isEmpty()) {
            throw new CodegenException(
                "unsupported collection shapes:\n  " + String.join("\n  ", errors)
            );
        }

        for (Shape shape : collections) {
            String signature = codecSignature(model, shape, signatures, new HashSet<>());
            representativeBySignature.merge(
                signature,
                shape.getId(),
                (left, right) -> left.toString().compareTo(right.toString()) <= 0 ? left : right
            );
        }

        Map<ShapeId, ShapeId> representatives = new HashMap<>();
        for (Shape shape : collections) {
            String signature = signatures.get(shape.getId());
            representatives.put(shape.getId(), representativeBySignature.get(signature));
        }
        return Map.copyOf(representatives);
    }

    private static String codecSignature(
        Model model,
        Shape shape,
        Map<ShapeId, String> signatures,
        Set<ShapeId> visiting
    ) {
        String known = signatures.get(shape.getId());
        if (known != null) {
            return known;
        }
        if (!visiting.add(shape.getId())) {
            return "recursive(" + shape.getId() + ")";
        }

        String signature;
        if (shape instanceof ListShape list) {
            Shape member = model.expectShape(list.getMember().getTarget());
            signature = "list["
                + (list.hasTrait(SparseTrait.class) ? "sparse," : "dense,")
                + (list.hasTrait(UniqueItemsTrait.class) ? "set" : "list")
                + "](" + codecSignature(model, member, signatures, visiting) + ")";
        } else if (shape instanceof MapShape map) {
            Shape key = model.expectShape(map.getKey().getTarget());
            Shape value = model.expectShape(map.getValue().getTarget());
            signature = "map["
                + (map.hasTrait(SparseTrait.class) ? "sparse" : "dense")
                + "](" + codecSignature(model, key, signatures, visiting)
                + "," + codecSignature(model, value, signatures, visiting) + ")";
        } else {
            signature = switch (shape.getType()) {
                case STRING, ENUM -> "string";
                case INTEGER, INT_ENUM -> "integer";
                case STRUCTURE, UNION -> shape.getType() + "(" + shape.getId() + ")";
                default -> shape.getType().toString();
            };
        }

        visiting.remove(shape.getId());
        signatures.put(shape.getId(), signature);
        return signature;
    }

    private static void validateList(Model model, ListShape list, List<String> errors) {
        Shape member = model.expectShape(list.getMember().getTarget());
        checkLeaf(list, member, errors);
        if (list.hasTrait(UniqueItemsTrait.class)) {
            if (isCollection(member)) {
                errors.add(list.getId() + ": @uniqueItems is not supported on collections of collections");
            }
            switch (member.getType()) {
                case STRUCTURE, UNION -> errors.add(
                    list.getId() + ": @uniqueItems is not supported for structure or union members"
                        + " because generated structures do not implement hashCode"
                );
                default -> {}
            }
            if (list.hasTrait(SparseTrait.class)) {
                errors.add(list.getId() + ": @sparse is not supported on @uniqueItems collections");
            }
        }
    }

    private static void validateMap(Model model, MapShape map, List<String> errors) {
        Shape value = model.expectShape(map.getValue().getTarget());
        checkLeaf(map, value, errors);
        if (map.hasTrait(SparseTrait.class) && !isCollection(value)) {
            switch (value.getType()) {
                case STRUCTURE, UNION, BIG_INTEGER, BIG_DECIMAL -> {}
                default -> LOGGER.warning(
                    map.getId() + ": @sparse is ignored for maps of " + value.getType()
                        + "; null values are not representable and will throw at serialization time"
                );
            }
        }
    }

    private static void checkLeaf(Shape container, Shape element, List<String> errors) {
        switch (element.getType()) {
            case DOCUMENT -> errors.add(
                container.getId() + ": collections of " + element.getType() + " are not supported"
            );
            default -> {}
        }
    }
}

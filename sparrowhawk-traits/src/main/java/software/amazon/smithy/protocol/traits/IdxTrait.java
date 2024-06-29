/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.protocol.traits;

import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public class IdxTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("smithy.protocols#idx");

    private final int value;

    public IdxTrait(int value, SourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.value = value;
    }

    public IdxTrait(int value) {
        this(value, SourceLocation.NONE);
    }

    public int getValue() {
        return value;
    }

    @Override
    protected Node createNode() {
        return new NumberNode(value, getSourceLocation());
    }

    public static final class Provider implements TraitService {

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            IdxTrait result = new IdxTrait(
                value.expectNumberNode().getValue().intValue(),
                value.getSourceLocation()
            );
            result.setNodeCache(value);
            return result;
        }

        @Override
        public ShapeId getShapeId() {
            return ID;
        }
    }
}

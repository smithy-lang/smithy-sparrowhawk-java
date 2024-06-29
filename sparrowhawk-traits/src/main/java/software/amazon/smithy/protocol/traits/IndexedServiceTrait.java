/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.protocol.traits;

import java.util.Collections;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

public final class IndexedServiceTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithy.protocols#indexed");

    public IndexedServiceTrait(SourceLocation sourceLocation) {
        super(ID, sourceLocation);
    }

    public IndexedServiceTrait() {
        this(SourceLocation.NONE);
    }

    @Override
    protected Node createNode() {
        return new ObjectNode(Collections.emptyMap(), getSourceLocation());
    }

    public static final class Provider implements TraitService {

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            IndexedServiceTrait result = new IndexedServiceTrait(value.getSourceLocation());
            result.setNodeCache(value);
            return result;
        }

        @Override
        public ShapeId getShapeId() {
            return ID;
        }
    }
}

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

/**
 * The @sparrowhawkObject trait is used to designate a blob field that always contains a
 * serialized Sparrowhawk object.
 *
 * <p>Code generators should use this as a hint to type such fields as a base SparrowhawkObject.
 * Serializers <em>must</em> use this to avoid generating an unnecessary length prefix for these
 * blob fields during serialization and deserialization, as the serialized object will already
 * begin with its own length prefix.
 */
public final class SparrowhawkObjectTrait extends AbstractTrait {
    public static final ShapeId ID = ShapeId.from("smithy.protocols#sparrowhawkObject");

    public SparrowhawkObjectTrait() {
        this(SourceLocation.NONE);
    }

    public SparrowhawkObjectTrait(SourceLocation sourceLocation) {
        super(ID, sourceLocation);
    }

    @Override
    protected Node createNode() {
        return new ObjectNode(Collections.emptyMap(), getSourceLocation());
    }

    public static final class SparrowhawkObjectProvider implements TraitService {
        @Override
        public Trait createTrait(ShapeId target, Node value) {
            SparrowhawkObjectTrait result = new SparrowhawkObjectTrait(value.getSourceLocation());
            result.setNodeCache(value);
            return result;
        }

        @Override
        public ShapeId getShapeId() {
            return ID;
        }
    }
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.protocol.traits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.EventHeaderTrait;
import software.amazon.smithy.model.traits.EventPayloadTrait;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

/**
 * A validator for indexed services. If a shape is in the closure of an indexed service,
 * its members must be indexed, the ordering starts at 1 and has no gaps.
 */
public final class IndexedServiceTraitValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        List<ValidationEvent> events = new ArrayList<>();

        Set<ShapeId> shapesOfInterest = new HashSet<>();
        Set<ServiceShape> services = model.getServiceShapesWithTrait(IndexedServiceTrait.class);
        for (ServiceShape service : services) {
            new Walker(model).iterateShapes(service).forEachRemaining(shape -> {
                if ((shape.isStructureShape() || shape.isUnionShape())
                    && !shape.hasTrait(MixinTrait.class)) {
                    shapesOfInterest.add(shape.getId());
                }
            });
        }

        for (ShapeId shapeId : shapesOfInterest) {
            validateMembers(model, model.expectShape(shapeId), events);
        }

        return events;
    }

    private void validateMembers(
        Model model,
        Shape container,
        List<ValidationEvent> events
    ) {
        int lastIdx = 0;
        List<MemberShape> sorted = new ArrayList<>(container.members());
        sorted.sort(Comparator.comparingInt(t -> t.getTrait(IdxTrait.class).map(IdxTrait::getValue).orElse(-1)));

        for (MemberShape ms : sorted) {
            if (!ms.hasTrait(IdxTrait.class)) {
                if (ms.hasTrait(EventHeaderTrait.class) || ms.hasTrait(EventPayloadTrait.class)) {
                    continue;
                }
                Shape untraitedTarget = model.expectShape(ms.getTarget());
                if ((untraitedTarget.isBlobShape() || untraitedTarget.isUnionShape())
                    && untraitedTarget.hasTrait(StreamingTrait.class)) {
                    continue;
                }

                events.add(
                    error(
                        ms,
                        ms.getSourceLocation(),
                        String.format(
                            "Structure \"%s\" is in the closure of an indexed service, but its member "
                                + "\"%s\" is missing an idx trait",
                            ms.getContainer(),
                            ms.toShapeId()
                        )
                    )
                );

                continue;
            }

            IdxTrait trait = ms.expectTrait(IdxTrait.class);
            if (trait.getValue() == lastIdx) {
                events.add(error(ms, trait, String.format("Duplicate idx value \"%d\"", lastIdx)));
            } else if (trait.getValue() != ++lastIdx) {
                if (trait.getValue() - 1 == lastIdx) {
                    events.add(
                        error(
                            ms,
                            trait,
                            String.format(
                                "idx must increase monotonically starting at 1, no members found for idx "
                                    + "%d",
                                lastIdx
                            )
                        )
                    );

                } else {
                    events.add(
                        error(
                            ms,
                            trait,
                            String.format(
                                "idx must increase monotonically starting at 1, no members found for idxs "
                                    + "between %d and %d, inclusive",
                                lastIdx,
                                trait.getValue() - 1
                            )
                        )
                    );

                }
                lastIdx = trait.getValue();
            }
        }
    }
}

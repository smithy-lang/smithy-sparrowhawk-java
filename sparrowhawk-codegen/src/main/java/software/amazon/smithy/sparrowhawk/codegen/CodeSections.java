/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import software.amazon.smithy.utils.CodeSection;

public final class CodeSections {
    public record StartClassSection(StructureGenerator generator) implements CodeSection {}

    public record EndClassSection(StructureGenerator generator) implements CodeSection {}

    private CodeSections() {}
}

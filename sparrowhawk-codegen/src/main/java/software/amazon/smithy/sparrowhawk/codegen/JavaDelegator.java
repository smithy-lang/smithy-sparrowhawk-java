/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;

public class JavaDelegator extends WriterDelegator<JavaWriter> {
    public JavaDelegator(FileManifest fileManifest, SymbolProvider symbolProvider, SparrowhawkSettings settings) {
        super(fileManifest, symbolProvider, new JavaWriter.JavaWriterFactory(settings));
    }
}

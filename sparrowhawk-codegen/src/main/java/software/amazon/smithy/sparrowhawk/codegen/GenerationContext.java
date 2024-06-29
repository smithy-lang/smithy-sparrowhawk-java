/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

public record GenerationContext(
    Model model, SparrowhawkSettings settings, SymbolProvider symbolProvider,
    WriterDelegator<JavaWriter> writerDelegator, List<SparrowhawkIntegration> integrations,
    FileManifest fileManifest
)
    implements CodegenContext<SparrowhawkSettings, JavaWriter, SparrowhawkIntegration>,
    ToSmithyBuilder<GenerationContext> {

    public static Builder builder() { return new Builder(); }

    @Override
    public SmithyBuilder<GenerationContext> toBuilder() {
        return new Builder().model(model)
            .settings(settings)
            .symbolProvider(symbolProvider)
            .writerDelegator(writerDelegator)
            .integrations(integrations)
            .fileManifest(fileManifest);
    }

    public static final class Builder implements SmithyBuilder<GenerationContext> {

        private Model model;
        private SparrowhawkSettings settings;
        private SymbolProvider symbolProvider;
        private WriterDelegator<JavaWriter> writerDelegator;
        private List<SparrowhawkIntegration> integrations = new ArrayList<>();
        private FileManifest fileManifest;

        public Builder model(Model m) { this.model = m; return this; }

        public Builder settings(SparrowhawkSettings s) { this.settings = s; return this; }

        public Builder symbolProvider(SymbolProvider s) { this.symbolProvider = s; return this; }

        public Builder writerDelegator(WriterDelegator<JavaWriter> w) { this.writerDelegator = w; return this; }

        public Builder integrations(List<SparrowhawkIntegration> i) { this.integrations = i; return this; }

        public Builder fileManifest(FileManifest f) { this.fileManifest = f; return this; }

        @Override
        public GenerationContext build() {
            return new GenerationContext(model, settings, symbolProvider, writerDelegator, integrations, fileManifest);
        }
    }
}

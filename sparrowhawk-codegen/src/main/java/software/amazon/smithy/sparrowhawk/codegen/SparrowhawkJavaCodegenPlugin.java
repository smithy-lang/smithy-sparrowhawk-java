/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.ShapeGenerationOrder;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Plugin to execute Sparrowhawk Java code generation
 */
@SmithyUnstableApi
public final class SparrowhawkJavaCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public String getName() {
        return "sparrowhawk-java-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        var director = new CodegenDirector<JavaWriter, SparrowhawkIntegration, GenerationContext, SparrowhawkSettings>();
        director.integrationClass(SparrowhawkIntegration.class);
        var settings = director.settings(SparrowhawkSettings.class, context.getSettings());
        director.service(settings.getService());
        director.model(context.getModel());
        director.fileManifest(context.getFileManifest());
        director.directedCodegen(new DirectedSparrowhawkCodegen());
        director.shapeGenerationOrder(ShapeGenerationOrder.TOPOLOGICAL);
        director.run();
    }
}

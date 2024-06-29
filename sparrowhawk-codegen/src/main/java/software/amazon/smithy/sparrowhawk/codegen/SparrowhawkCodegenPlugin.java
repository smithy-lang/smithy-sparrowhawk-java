/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

public class SparrowhawkCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public String getName() {
        return "sparrowhawk-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        var director = new CodegenDirector<JavaWriter, SparrowhawkIntegration, GenerationContext, SparrowhawkSettings>();

        var settings = SparrowhawkSettings.from(context.getSettings());
        director.settings(settings);
        director.directedCodegen(new DirectedSparrowhawkCodegen());
        director.fileManifest(context.getFileManifest());
        director.service(settings.getService());
        director.model(context.getModel());
        director.integrationClass(SparrowhawkIntegration.class);
        director.performDefaultCodegenTransforms();
        director.createDedicatedInputsAndOutputs();
        director.run();

    }
}

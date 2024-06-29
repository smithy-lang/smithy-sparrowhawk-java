/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import java.util.function.BiFunction;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.Shape;

public final class JavaWriter extends SymbolWriter<JavaWriter, JavaImportContainer> {

    private final SparrowhawkSettings settings;
    private final String packageName;
    private boolean plainFile;

    public JavaWriter(SparrowhawkSettings settings, String packageName) {
        super(new JavaImportContainer(packageName, settings.getHeader()));
        this.settings = settings;
        this.packageName = packageName;
        trimBlankLines();
        trimTrailingSpaces();
        putFormatter('T', new JavaSymbolFormatter());
    }

    public void setPlain(boolean plain) {
        this.plainFile = plain;
    }

    @Override
    public String toString() {
        if (plainFile) {
            return super.toString();
        }
        return getImportContainer().toString() + "\n\n" + super.toString();
    }

    private class JavaSymbolFormatter implements BiFunction<Object, String, String> {
        @Override
        public String apply(Object type, String indent) {
            if (type instanceof Symbol typeSymbol) {
                for (SymbolReference sr : typeSymbol.getReferences()) {
                    addUseImports(sr);
                }
                addImport(typeSymbol, null, SymbolReference.ContextOption.USE);
                return typeSymbol.getProperty("shape", Shape.class).map(shape -> {
                    if (shape.isListShape()) {
                        addImport(typeSymbol, null, SymbolReference.ContextOption.USE);
                        return "%s<%s>".formatted(
                            getImportContainer().getAlias(typeSymbol),
                            apply(typeSymbol.getProperty("value").orElseThrow(), "")
                        );
                    } else if (shape.isMapShape()) {
                        addImport(typeSymbol, null, SymbolReference.ContextOption.USE);
                        return "%s<%s, %s>".formatted(
                            getImportContainer().getAlias(typeSymbol),
                            apply(typeSymbol.getProperty("key").orElseThrow(), ""),
                            apply(typeSymbol.getProperty("value").orElseThrow(), "")
                        );
                    }
                    return getImportContainer().getAlias(typeSymbol);
                }).orElseGet(() -> getImportContainer().getAlias(typeSymbol));
            } else if (type instanceof SymbolReference typeSymbol) {
                getImportContainer().importReference(typeSymbol);
                return getImportContainer().getAlias(typeSymbol);
            } else {
                throw new CodegenException(
                    "Invalid type provided to $T. Expected a Symbol, but found `" + type + "`"
                );
            }
        }
    }

    public static class JavaWriterFactory implements Factory<JavaWriter> {
        private final SparrowhawkSettings settings;

        public JavaWriterFactory(SparrowhawkSettings settings) {
            this.settings = settings;
        }

        @Override
        public JavaWriter apply(String filename, String namespace) {
            return new JavaWriter(settings, namespace);
        }
    }
}

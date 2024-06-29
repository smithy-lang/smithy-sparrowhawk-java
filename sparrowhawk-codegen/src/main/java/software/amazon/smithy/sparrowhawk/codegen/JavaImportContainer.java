/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolReference;

final class JavaImportContainer implements ImportContainer {

    private final String packageName;
    private final String header;
    private final Map<String, Symbol> aliasesToSymbol = new HashMap<>();
    private final Map<Symbol, String> symbolsToAlias = new TreeMap<>(
        Comparator.comparing(Symbol::getNamespace).thenComparing(Symbol::getName)
    );

    private final Map<String, Symbol> staticAliasesToSymbol = new HashMap<>();
    private final Map<Symbol, String> staticSymbolsToAlias = new TreeMap<>(
        Comparator.comparing(Symbol::getNamespace).thenComparing(Symbol::getName)
    );


    JavaImportContainer(String packageName, String header) {
        this.packageName = packageName;
        this.header = header;
    }

    @Override
    public void importSymbol(Symbol symbol, String alias) {
        importSymbol(symbol);
    }

    @Override
    public void importSymbol(Symbol symbol) {
        if (symbolsToAlias.containsKey(symbol)) {
            return;
        }

        if (symbol.getNamespace() == null || symbol.getNamespace().isEmpty()) {
            symbolsToAlias.put(symbol, symbol.getName());
            aliasesToSymbol.put(symbol.getName(), symbol);
            return;
        }

        if (!aliasesToSymbol.containsKey(symbol.getName())) {
            aliasesToSymbol.put(symbol.getName(), symbol);
            symbolsToAlias.put(symbol, symbol.getName());
        } else {
            symbolsToAlias.put(symbol, symbol.getNamespace() + "." + symbol.getName());
            aliasesToSymbol.put(symbol.getNamespace() + "." + symbol.getName(), symbol);
        }
    }

    public void importReference(SymbolReference reference) {
        var refSymbol = reference.getSymbol();
        if (!reference.getOptions().contains(CommonSymbols.UseOption.STATIC)) {
            importSymbol(refSymbol);
        } else {
            if (staticSymbolsToAlias.containsKey(refSymbol)) {
                return;
            }
            if (staticAliasesToSymbol.containsKey(refSymbol.getName())) {
                return;
            }
            staticSymbolsToAlias.put(refSymbol, refSymbol.getName());
            staticAliasesToSymbol.put(refSymbol.getName(), refSymbol);
        }
    }

    public String getAlias(Symbol symbol) {
        return symbolsToAlias.get(symbol);
    }

    public String getAlias(SymbolReference ref) {
        Symbol refSymbol = ref.getSymbol();
        if (ref.getOptions().contains(CommonSymbols.UseOption.STATIC)) {
            var alias = staticSymbolsToAlias.get(refSymbol);
            if (alias == null) {
                alias = refSymbol.getNamespace() + "." + refSymbol.getName();
            }
            return alias;
        }
        return getAlias(refSymbol);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (header != null) {
            sb.append(header);
            sb.append('\n');
        }

        sb.append("package ")
            .append(packageName)
            .append(";\n\n");

        for (Map.Entry<Symbol, String> e : symbolsToAlias.entrySet()) {
            if (packageName.equals(e.getKey().getNamespace())
                || e.getKey().getNamespace() == null
                || e.getKey().getNamespace().isEmpty()) {
                continue;
            }
            sb.append("import ")
                .append(e.getKey().getNamespace())
                .append('.')
                .append(e.getKey().getName())
                .append(";\n");
        }
        if (!staticSymbolsToAlias.isEmpty()) {
            sb.append("\n");
            for (Map.Entry<Symbol, String> e : staticSymbolsToAlias.entrySet()) {
                sb.append("import static ")
                    .append(e.getKey().getNamespace())
                    .append('.')
                    .append(e.getKey().getName())
                    .append(";\n");
            }
        }
        return sb.toString();
    }
}

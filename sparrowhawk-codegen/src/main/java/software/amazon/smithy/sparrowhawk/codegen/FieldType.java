/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import software.amazon.smithy.java.sparrowhawk.KConstants;

public enum FieldType {
    VARINT("varint", "VARINT", KConstants.T_VARINT),
    LIST("list", "LIST", KConstants.T_LIST),
    FOUR_BYTE("fourByte", "FOUR_BYTE", KConstants.T_FOUR),
    EIGHT_BYTE("eightByte", "EIGHT_BYTE", KConstants.T_EIGHT),
    ;

    public final String lowercaseId;
    public final String uppercaseId;
    public final int wireType;

    FieldType(String lowercaseId, String uppercaseId, int wireType) {
        this.lowercaseId = lowercaseId;
        this.uppercaseId = uppercaseId;
        this.wireType = wireType;
    }
}

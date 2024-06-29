/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

public interface SparrowhawkObject {
    void decodeFrom(SparrowhawkDeserializer d);

    void encodeTo(SparrowhawkSerializer s);

    int size();
}

$version: "2"

namespace demo

use smithy.protocols#idx

service DemoService {
    operations: [
        DemoOperation
    ]
}

operation DemoOperation {
    input: DemoInput
}

structure DemoInput {
    @idx(1)
    @required
    str: String

    @idx(2)
    @required
    f: PrimitiveFloat = 0

    @idx(3)
    @required
    d: PrimitiveDouble = 0

    @idx(4)
    @required
    i: PrimitiveInteger = 0

    @idx(5)
    @required
    bytes: Blob

    @idx(6)
    nested: NestedStructure
}

structure NestedStructure {
    @idx(1)
    @required
    innerStr: String

    @idx(2)
    @required
    list: VarintList
}

list VarintList {
    member: PrimitiveInteger
}

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
}

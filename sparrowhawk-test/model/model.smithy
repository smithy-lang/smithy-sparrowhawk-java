$version: "2"

namespace demo

use demo.other#CrossNsIntListList
use smithy.protocols#idx

service DemoService {
    operations: [
        DemoOperation
        MapCollectionsOperation
        NestedCollectionsOperation
        SkipOperation
    ]
}

operation SkipOperation {
    input: SkipTarget
}

structure SkipTarget {
    @idx(1)
    intListList: IntListList
}

operation NestedCollectionsOperation {
    input: NestedCollectionsInput
}

structure NestedCollectionsInput {
    @idx(1)
    intListList: IntListList

    @idx(2)
    stringListList: StringListList

    @idx(3)
    structListList: StructListList

    @idx(4)
    blobListList: BlobListList

    @idx(5)
    timestampListList: TimestampListList

    @idx(6)
    doubleListList: DoubleListList

    @idx(7)
    intListListList: IntListListList

    @idx(8)
    intListListListList: IntListListListList

    @idx(9)
    stringMapList: StringMapList

    @idx(10)
    structMapList: StructMapList

    @idx(11)
    intMapMapList: IntMapMapList

    @idx(12)
    intListMapList: IntListMapList

    @idx(13)
    longListMap: LongListMap

    @idx(14)
    stringListMap: StringListMap

    @idx(15)
    blobListMap: BlobListMap

    @idx(16)
    structListMap: StructListMap

    @idx(17)
    timestampListMap: TimestampListMap

    @idx(18)
    intListListMap: IntListListMap

    @idx(19)
    intListMapMap: IntListMapMap

    @idx(20)
    tree: TreeNode

    @idx(21)
    golden: GoldenNestedList

    @idx(22)
    @required
    requiredIntListList: IntListList

    @idx(23)
    sparseIntListList: SparseIntListList

    @idx(24)
    listOfSparseIntList: ListOfSparseIntList

    @idx(25)
    sparseStringMapList: SparseStringMapList

    @idx(26)
    sparseStringListMap: SparseStringListMap

    @idx(27)
    sparseStringMapMap: SparseStringMapMap

    @idx(28)
    sparseIntMapMapMap: SparseIntMapMapMap

    @idx(29)
    goldenSparse: GoldenSparseNestedList

    @idx(30)
    crossNs: CrossNsIntListList

    @idx(31)
    bigIntegerList: BigIntegerList

    @idx(32)
    bigDecimalList: BigDecimalList

    @idx(33)
    bigIntegerMap: BigIntegerMap

    @idx(34)
    bigDecimalMap: BigDecimalMap

    @idx(35)
    bigIntegerListList: BigIntegerListList

    @idx(36)
    bigDecimalMapList: BigDecimalMapList

    @idx(37)
    sparseBigIntegerList: SparseBigIntegerList

    @idx(38)
    sparseBigDecimalMap: SparseBigDecimalMap

    @idx(39)
    listOfSparseBigIntegerList: ListOfSparseBigIntegerList

    @idx(40)
    sparseBigIntegerMap: SparseBigIntegerMap

    @idx(41)
    sparseBigDecimalList: SparseBigDecimalList

    @idx(42)
    intSet: IntSet

    @idx(43)
    stringSet: StringSet

    @idx(44)
    blobSet: BlobSet

    @idx(45)
    longSet: LongSet

    @idx(46)
    bigIntegerSet: BigIntegerSet

    @idx(47)
    bigDecimalSet: BigDecimalSet

    @idx(48)
    intSetList: IntSetList

    @idx(49)
    stringSetMap: StringSetMap

    @idx(50)
    sparseIntSetList: SparseIntSetList

    @idx(51)
    duplicateIntSet: IntSetDuplicate
}

list BigIntegerList {
    member: BigInteger
}

list BigDecimalList {
    member: BigDecimal
}

map BigIntegerMap {
    key: String
    value: BigInteger
}

map BigDecimalMap {
    key: String
    value: BigDecimal
}

list BigIntegerListList {
    member: BigIntegerList
}

list BigDecimalMapList {
    member: BigDecimalMap
}

@sparse
list SparseBigIntegerList {
    member: BigInteger
}

@sparse
map SparseBigDecimalMap {
    key: String
    value: BigDecimal
}

@sparse
map SparseBigIntegerMap {
    key: String
    value: BigInteger
}

@sparse
list SparseBigDecimalList {
    member: BigDecimal
}

list ListOfSparseBigIntegerList {
    member: SparseBigIntegerList
}

@uniqueItems
list IntSet {
    member: Integer
}

@uniqueItems
list IntSetDuplicate {
    member: Integer
}

@uniqueItems
list StringSet {
    member: String
}

@uniqueItems
list BlobSet {
    member: Blob
}

@uniqueItems
list LongSet {
    member: PrimitiveLong
}

@uniqueItems
list BigIntegerSet {
    member: BigInteger
}

@uniqueItems
list BigDecimalSet {
    member: BigDecimal
}

list IntSetList {
    member: IntSet
}

map StringSetMap {
    key: String
    value: StringSet
}

@sparse
list SparseIntSetList {
    member: IntSet
}

@sparse
list SparseIntListList {
    member: VarintList
}

@sparse
list SparseIntList {
    member: PrimitiveInteger
}

list ListOfSparseIntList {
    member: SparseIntList
}

@sparse
list SparseStringMapList {
    member: StringMap
}

@sparse
map SparseStringListMap {
    key: String
    value: StringList
}

@sparse
map SparseStringMapMap {
    key: String
    value: StringMap
}

@sparse
map SparseIntMapMapMap {
    key: String
    value: IntMapMap
}

structure GoldenNestedList {
    @idx(1)
    @required
    nested: IntListList
}

structure GoldenSparseNestedList {
    @idx(1)
    @required
    nested: SparseIntListList
}

structure TreeNode {
    @idx(1)
    @required
    name: String

    @idx(2)
    children: TreeNodeListList
}

list TreeNodeList {
    member: TreeNode
}

list TreeNodeListList {
    member: TreeNodeList
}

list IntListList {
    member: VarintList
}

list IntListListList {
    member: IntListList
}

list IntListListListList {
    member: IntListListList
}

list StringList {
    member: String
}

list StringListList {
    member: StringList
}

list StructList {
    member: NestedStructure
}

list StructListList {
    member: StructList
}

list BlobList {
    member: Blob
}

list BlobListList {
    member: BlobList
}

list TimestampList {
    member: Timestamp
}

list TimestampListList {
    member: TimestampList
}

list DoubleList {
    member: PrimitiveDouble
}

list DoubleListList {
    member: DoubleList
}

list StringMapList {
    member: StringMap
}

list StructMapList {
    member: StructMap
}

list IntMapMapList {
    member: IntMapMap
}

list IntListMapList {
    member: IntListMap
}

list LongList {
    member: PrimitiveLong
}

map LongListMap {
    key: String
    value: LongList
}

map StringListMap {
    key: String
    value: StringList
}

map BlobListMap {
    key: String
    value: BlobList
}

map StructListMap {
    key: String
    value: StructList
}

map TimestampListMap {
    key: String
    value: TimestampList
}

map IntListListMap {
    key: String
    value: IntListList
}

map IntListMapMap {
    key: String
    value: IntListMap
}

operation DemoOperation {
    input: DemoInput
}

operation MapCollectionsOperation {
    input: MapCollectionsInput
}

structure MapCollectionsInput {
    @idx(1)
    stringMapMap: StringMapMap

    @idx(2)
    intMapMapMap: IntMapMapMap

    @idx(3)
    structMapMap: StructMapMap

    @idx(4)
    intListMap: IntListMap
}

map StringMapMap {
    key: String
    value: StringMap
}

map StringMap {
    key: String
    value: String
}

map IntMapMapMap {
    key: String
    value: IntMapMap
}

map IntMapMap {
    key: String
    value: IntMap
}

map IntMap {
    key: String
    value: PrimitiveInteger
}

map StructMapMap {
    key: String
    value: StructMap
}

map StructMap {
    key: String
    value: NestedStructure
}

map IntListMap {
    key: String
    value: VarintList
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

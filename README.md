# [DRAFT] Sparrowhawk Format

The Sparrowhawk format is named after the [American kestrel](https://en.wikipedia.org/wiki/American_kestrel). It's the smallest bird in its family, but can still achieve speeds of over 100 MPH in dives while hunting. Our format aims for similar goals: we want something small and fast. By leaning in to Smithy model semantics, we can offer a payload that is both compact and fast to encode and decode.

Sparrowhawk aims to achieve these goals, ranked by priority:

1. **Safe and easy schema evolution**: Schemas that describe Sparrowhawk payloads must be able to reliably change over time. In particular, clients must be able to handle receiving payloads with fields they don’t know about and servers must be able to decode payloads sent by all released clients, including releases that were rolled back. sparrowhawk will use Smithy as its modeling language, so we must be able to support [Smithy’s rules for backwards compatibility](https://smithy.io/2.0/guides/evolving-models.html).
2. **Performance**: At minimum, it must be faster than comparable serialization formats.
3. **Streaming serialized objects**: All messages in the Sparrowhawk format are length-prefixed to allow for simple and unambiguous parceling of streams of data into individual messages. sparrowhawk does not depend on its transport mechanism for providing payload boundaries so it is not subject to their limitations and can easily be migrated to new wire transports.
4. **Payload compactness**: Sparrowhawk is 50% more compact than the CBOR format used for RPCv2 CBOR and 70% more compact than JSON for the same payloads. It also realizes a 5-10% size reduction over other integer-keyed serialization formats.
5. **Model-less payload reflection and unknown field propagation**: Sparrowhawk payloads may be losslessly explored with partial or no knowledge of the schemas that define them, allowing for a limited form of model-oblivious parsing. Payloads may be partially decoded, modified, and forwarded without loss of data, allowing for proxy services to read, update, and forward requests and responses without needing full deployments alongside every model change.

## Data types

### Varints

Sparrowhawk varints are:

1. **unsigned**: it is the responsibility of the application to confer signed semantics to varints when reading or writing application data in a payload. All varints written as part of the format itself are unsigned.
2. **little-endian**: this allows for more performant decoding routines on modern processors which are overwhelmingly little-endian
3. **prefix-encoded**: the number of leading zeros in the first byte of a varint describes how many additional bytes must be read to fully decode the varint. This can be more efficiently parsed than the more common LEB128 encoding, which uses one bit per each byte to indicate if another byte follows and therefore requires one branch per byte.

Here are some sample varints:

|Value	|Hex	|First byte	|
|---	|---	|---	|
|0	|01	|00000001	|
|1	|03	|00000011	|
|127	|ff	|11111111	|
|128	|02 02	|00000010	|
|16383	|fe ff	|11111110	|
|16384	|04 00 02	|00000100	|
|2097151	|fc ff ff	|11111100	|
|2097152	|08 00 00 02	|00001000	|
|Long.MIN_VALUE	|00 ff ff ff ff ff ff ff 7f	|00000000	|
|Long.MAX_VALUE	|00 00 00 00 00 00 00 00 80	|00000000	|

The number of bytes used to encode a varint is described by adding 1 to the number of trailing least significant zero bits in the low byte. In the table above, values less than 128 all begin with 1, indicating the varint is encoded in a single byte. 128 begins with 10, meaning a two-byte encoding.

**Encoding a varint**

This demonstrates how a big-endian integer may be encoded into a little-endian varint. A big-endian “machine” representation is used because it’s easier to read; this would almost certainly be a little-endian encoding in reality.

```
 byte 0   byte 1   byte 2   byte 3
// int 8675309 in big-endian form
00000000 10000100 01011111 11101101
// Determine the number of bits used to represent this number by counting
// leading zeros and subtracting from 32. There are eight consecutive
// zeros in the high bits, so this is a 24-bit number. Varints can encode
// seven bits per byte, so we need four bytes.
// The value "1000" is pushed on to the low bits, shifting everything else to
// the left by 4.
00001000 01000101 11111110 11011000
// Rewrite little-endian
11011000 11111110 01000101 00001000
```

**Decoding a varint**

This demonstrates how a little-endian varint may be decoded into a big-endian integer. Like before, big-endian is used to make the example easier to read.

```
 byte 0   byte 1   byte 2   byte 3
11011000 11111110 01000101 00001000 // 8675309, encoded
00001000 01000101 11111110 11011000 // rewrite big-endian
00000000 10000100 01011111 11101101 // shift right 4 to remove low byte length bits
```

### Lists

Lists are composed of a list length and list values. A list length value is a varint describing the number of elements in the following list. The value contains type information in the low bits which describes how to decode the elements:

|Element type	|Bit pattern	|Meaning	|
|---	|---	|---	|
|Bytes	|0b0	|(n >> 1) bytes follow	|
|Lists	|0b001	|(n >> 3) lists follow	|
|Varints	|0b011	|(n >> 3) varints follow	|
|Four-byte items	|0b101	|(n >> 3) four-byte values follow	|
|Eight-byte items	|0b111	|(n >> 3) eight-byte values follow	|

When the low bit is 0, the value of the varint shifted right by one is the number of bytes that follow the varint. In all other cases, the value shifted right by three is the number of elements of the given type that follow the varint.

When the list type is varint, four-byte, or eight-byte, items are decoded in the obvious way. If a list has five varints, you can read five varints from it. Lists that contain lists are recursively decoded: the first element of a nested list has its own list type and is decoded according to the table above. Because structures and strings are both encoded as byte lists, a list of either item type is encoded as a nested list. The top-level list contains lists of byte lists, and each byte list is an encoded item.

A nested list of lists **MAY** contain heterogeneous list types. For example, the first list in a list of lists may be a byte list and the second list may be a varint list. Applications **MAY** choose to enforce that all sub-lists have the same type.

**Design note on list lengths**
A variable bit pattern was chosen to maximize the number of bytes that can be described by a one-byte varint. A single-byte varint can contain seven bits of data. Since byte lists are used to encode structures, strings, and blobs, they are the most common form of list lengths used. A two-bit type pattern would only leave five remaining bits in our one-byte varint, or a cap of 32 bytes. Only using the low bit doubles the limit to 64 bytes, which is enough to encode most strings with single-byte length prefixes.

Originally, the list length was conceived as just a varint that describes the number of bytes that follow it. After moving to the type-tag system, the loss of three bits to describe the four remaining types was deemed acceptable as each type serves as a multiplier on the element count given in the varint, and describing an element count is both more useful (for pre-sizing lists and eagerly validating list sizes) and more compact (none of the three-bit types can be encoded in one byte).

### Structures

A Sparrowhawk structure is a list of bytes (list type `0`). Following its list length value is are type sections and the data they describe. An empty structure may have zero type sections, in which case its length is zero. This is the canonical encoding of an empty structure.

Each type section opens with a varint. The low two bits of the varint describe the type of data contained in this type section:

|Type	|Bit pattern	|
|---	|---	|
|Lists	|0b00	|
|Varints	|0b01	|
|Four-byte items	|0b10	|
|Eight-byte items	|0b11	|

The third bit is a continuation bit. When set, a varint follows that describes the offset of this type section. An offset is an unsigned integer that designates the (n+1)th bitset, or the (n+1)th grouping of 61 fields of this data type. The varint shifted right by three bits is a 61-bit bitset describing the fields present in this type section. **NOTE**: a continuation field set with offset 0 describes fields 62-123, not fields 0-61. The type section describing the first 61 fields **MUST** not have the continuation bit set.

Illustrated:

```
+----------+-+--+
|  bitset  |c|tt|
+----------+-+--+
63   ...   3 2  0
```

The low two bits are the type, the third bit is the continuation flag, and the remainder is the field presence bitset. Each 1 in the bitset indicates a field that is present and a 0 indicates an absent field.

A Sparrowhawk structure is composed of fields that are uniquely identified by its type and an index number. The lowest bit in the bitset represents field 0, the next bit field 1, and so on. Index numbers are not shared between types: a varint with index 0 is distinct from a list with index 0 because they have separate types and therefore separate field presence bitsets.

Following a fieldset are the fields it indicates are present. Fields are written in order from smallest to largest index.

Field sets can be encoded in any order relative to each other. Continuation field sets can be in the front of an object and types can be written in any order.

All type sections in a serialized structure **MUST** be unique. It is an error to provide two type sections that could both encode the presence of a single field even if only one or neither actually does.

## Payload structure

A Sparrowhawk payload always begins with a list length. The list will generally be an encoded structure, but Sparrowhawk permits any type of top-level list. For example, a payload that is just a list of varints (list type 011) is valid Sparrowhawk.

### Sample payloads

```
JSON:
{
  "bool1": true,
  "d": 1.5,
  "f": 3.7,
  "i": 9182741,
  "l": 1,
  "optionalInt": 2147483647,
  "requiredStruct": {
    "string": "howdy",
    "timestamp": 123.456
  },
  "signedI": 1,
  "string": "string field 0 false",
  "stringMap": {}
}

Sparrowhawk (hex):

3202E603A8C28311050503D0FFFFFF1F15CDCC6C4017000000000000F83FB151737472
696E67206669656C6420302066616C736505451777BE9F1A2FDD5E401115686F776479

Sparrowhawk Annotated:
[Begin: Struct]
  3202: [varint] serialized size=70
  E603: fieldset varints 0 (raw value: 249), fields present: [1,2,3,4,5]
  A8C28311: [varint@1] i=9182741
  05: [varint@2] l=1
  05: [varint@3] signedI=1
  03: [varint@4] bool1=true
  D0FFFFFF1F: [varint@5] optionalInt=2147483647
  15: fieldset four-byte 0 (raw value: 10), fields present: [1]
  CDCC6C40: [four-byte@1] f=3.7
  17: fieldset eight-byte 0 (raw value: 11), fields present: [1]
  000000000000F83F: [eight-byte@1] d=1.5
  B1: fieldset lists 0 (raw value: 88), fields present: [1,2,3]
  51737472696E67206669656C6420302066616C7365: [list@1] string=string field 0 false
  [Begin list@2, name: stringMap, type: Map<String, String>]
    05: [varint] serialized size=1
  [End list@2, name: stringMap, type: stringMap]
  [Begin list@4, name: requiredStruct, type: OptionalStruct]
    45: [varint] serialized size=17
    17: fieldset eight-byte 0 (raw value: 11), fields present: [1]
    77BE9F1A2FDD5E40: [eight-byte@1] timestamp=123.456
    11: fieldset lists 0 (raw value: 8), fields present: [1]
    15686F776479: [list@1] string=howdy
  [End list@4, name: requiredStruct, type: OptionalStruct]
[End: Struct]
```



```
JSON:
{
  "bool1": true,
  "d": 1.5,
  "f": 3.700000047683716,
  "i": 9182741,
  "intList": [
    0,
    1,
    2,
    3,
    4
  ],
  "l": 1,
  "optionalInt": 2147483647,
  "requiredStruct": {
    "string": "howdy",
    "timestamp": 123.456
  },
  "signedI": 1,
  "string": "really cool string 0 true",
  "stringMap": {
    "key1": "value1",
    "key2": "value2",
    "key0": "value0"
  },
  "structList": [
    {
      "bool1": true,
      "d": 1.5,
      "f": 3.700000047683716,
      "i": 9182741,
      "l": 1,
      "optionalInt": 2147483647,
      "requiredStruct": {
        "string": "howdy",
        "timestamp": 123.456
      },
      "signedI": 1,
      "string": "really cool string 0 false",
      "stringMap": {},
      "time": 0.123
    }
  ],
  "time": 0.123
}

Sparrowhawk (hex):
a206e605a8c283110505d0ffffff1f0315cdcc6c4037000000000000f83fb0726891ed7cb
f3fe209657265616c6c7920636f6f6c20737472696e67203020747275659d3133116b6579
31116b657932116b657930331976616c7565311976616c7565321976616c756530139202e
605a8c283110505d0ffffff1f0315cdcc6c4037000000000000f83fb0726891ed7cbf3fb1
697265616c6c7920636f6f6c20737472696e6720302066616c736501411777be9f1a2fdd5
e401115686f776479411777be9f1a2fdd5e401115686f776479570105090d11


Annotated:[Struct (size: 212, type: sparrowhawk#CodegenStruct)]
    [TypeSection (type: varints, fields: [0, 1, 2, 3, 5])]
        [Member (name: i, kIdx: 0, sIdx: 5, type: smithy.api#PrimitiveInteger)]9182741[/Member]
        [Member (name: l, kIdx: 1, sIdx: 6, type: smithy.api#PrimitiveLong)]1[/Member]
        [Member (name: signedI, kIdx: 2, sIdx: 7, type: smithy.api#PrimitiveInteger)]1[/Member]
        [Member (name: optionalInt, kIdx: 3, sIdx: 10, type: smithy.api#Integer)]2147483647[/Member]
        [Member (name: bool1, kIdx: 5, sIdx: 14, type: smithy.api#PrimitiveBoolean)]true[/Member]
    [/TypeSection]
    [TypeSection (type: four-byte items, fields: [0])]
        [Member (name: f, kIdx: 0, sIdx: 9, type: smithy.api#PrimitiveFloat)]3.7[/Member]
    [/TypeSection]
    [TypeSection (type: eight-byte items, fields: [0, 1])]
        [Member (name: d, kIdx: 0, sIdx: 8, type: smithy.api#PrimitiveDouble)]1.5[/Member]
        [Member (name: time, kIdx: 1, sIdx: 22, type: smithy.api#Timestamp)]1970-01-01T00:00:00.123Z[/Member]
    [/TypeSection]
    [TypeSection (type: lists, fields: [0, 1, 2, 3, 6])]
        [Member (name: string, kIdx: 0, sIdx: 1, type: smithy.api#String)]really cool string 0 true[/Member]
        [Member (name: stringMap, kIdx: 1, sIdx: 2, type: sparrowhawk#StringMap)]
            [Map (size: 39, keys: 3, valueType: smithy.api#String)]
                [Entry (key: key1)]value1[/Entry]
                [Entry (key: key2)]value2[/Entry]
                [Entry (key: key0)]value0[/Entry]
        [/Member]
        [Member (name: structList, kIdx: 2, sIdx: 3, type: sparrowhawk#StructList)]
            [List (size: 1, valueType: sparrowhawk#CodegenStruct)]
                [Struct (size: 82, type: sparrowhawk#CodegenStruct)]
                    [TypeSection (type: varints, fields: [0, 1, 2, 3, 5])]
                        [Member (name: i, kIdx: 0, sIdx: 5, type: smithy.api#PrimitiveInteger)]9182741[/Member]
                        [Member (name: l, kIdx: 1, sIdx: 6, type: smithy.api#PrimitiveLong)]1[/Member]
                        [Member (name: signedI, kIdx: 2, sIdx: 7, type: smithy.api#PrimitiveInteger)]1[/Member]
                        [Member (name: optionalInt, kIdx: 3, sIdx: 10, type: smithy.api#Integer)]2147483647[/Member]
                        [Member (name: bool1, kIdx: 5, sIdx: 14, type: smithy.api#PrimitiveBoolean)]true[/Member]
                    [/TypeSection]
                    [TypeSection (type: four-byte items, fields: [0])]
                        [Member (name: f, kIdx: 0, sIdx: 9, type: smithy.api#PrimitiveFloat)]3.7[/Member]
                    [/TypeSection]
                    [TypeSection (type: eight-byte items, fields: [0, 1])]
                        [Member (name: d, kIdx: 0, sIdx: 8, type: smithy.api#PrimitiveDouble)]1.5[/Member]
                        [Member (name: time, kIdx: 1, sIdx: 22, type: smithy.api#Timestamp)]1970-01-01T00:00:00.123Z[/Member]
                    [/TypeSection]
                    [TypeSection (type: lists, fields: [0, 1, 3])]
                        [Member (name: string, kIdx: 0, sIdx: 1, type: smithy.api#String)]really cool string 0 false[/Member]
                        [Member (name: stringMap, kIdx: 1, sIdx: 2, type: sparrowhawk#StringMap)]
                            [Map (size: 0, keys: 0, valueType: smithy.api#String)][/Map]
                        [/Member]
                        [Member (name: requiredStruct, kIdx: 3, sIdx: 4, type: sparrowhawk#CodegenOptionalStruct)]
                            [Struct (size: 16, type: sparrowhawk#CodegenOptionalStruct)]
                                [TypeSection (type: eight-byte items, fields: [0])]
                                    [Member (name: timestamp, kIdx: 0, sIdx: 2, type: smithy.api#Double)]123.456[/Member]
                                [/TypeSection]
                                [TypeSection (type: lists, fields: [0])]
                                    [Member (name: string, kIdx: 0, sIdx: 1, type: smithy.api#String)]howdy[/Member]
                                [/TypeSection]
                            [/Struct]
                        [/Member]
                    [/TypeSection]
                [/Struct]
            [/List]
        [/Member]
        [Member (name: requiredStruct, kIdx: 3, sIdx: 4, type: sparrowhawk#CodegenOptionalStruct)]
            [Struct (size: 16, type: sparrowhawk#CodegenOptionalStruct)]
                [TypeSection (type: eight-byte items, fields: [0])]
                    [Member (name: timestamp, kIdx: 0, sIdx: 2, type: smithy.api#Double)]123.456[/Member]
                [/TypeSection]
                [TypeSection (type: lists, fields: [0])]
                    [Member (name: string, kIdx: 0, sIdx: 1, type: smithy.api#String)]howdy[/Member]
                [/TypeSection]
            [/Struct]
        [/Member]
        [Member (name: intList, kIdx: 6, sIdx: 15, type: sparrowhawk#IntList)]
            [List (size: 5, valueType: smithy.api#PrimitiveInteger)]
                [> 0]
                [> 1]
                [> 2]
                [> 3]
                [> 4]
            [/List]
        [/Member]
    [/TypeSection]
[/Struct]
```
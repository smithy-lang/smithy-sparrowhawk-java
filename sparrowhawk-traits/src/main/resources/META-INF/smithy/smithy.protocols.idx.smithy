$version: "2.0"

namespace smithy.protocols

/// Indicates that a particular service is indexed.
/// An indexed service must apply the @idx trait to every structure or union member in its closure,
/// except those members that are used for streaming payloads.
@trait(selector: "service")
structure indexed {}

/// Member index for an indexed protocol, used by an indexed service.
/// idx values must start at 1 and increase monotonically by 1 with no gaps.
@trait(selector: ":is(structure, union) :not([trait|mixin]) > member")
@range(min: 1)
integer idx

/// Marks a structure's place in an index hierarchy.
/// Some indexed protocols allow for composition of indexes from related shapes into one index space
/// disambiguating conflicts between idx values. This is largely useful for non-Smithy protocols that
/// are lossily translated to Smithy.
@trait(selector: "structure :not([trait|mixin])")
@length(min: 1)
list hierarchicalIdx {
    member: ChildId
}

/// The shapeId of a child structure in a hierarchicalIdx relationship.
@idRef(selector: "structure :not([trait|mixin])")
@private
string ChildId

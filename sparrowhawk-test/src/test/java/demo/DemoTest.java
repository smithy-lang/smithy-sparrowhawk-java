package demo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.sparrowhawk.codegen.Enhancer;

class DemoTest {
    private static final Model MODEL = new ModelAssembler()
        .addImport(Paths.get(System.getProperty("user.dir"), "model", "model.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();

    @Test
    public void enhance() {
        var obj = new DemoInput();
        obj.setStr("hello");
        obj.setD(1.234);
        obj.setI(-500);
        obj.setF(Float.MAX_VALUE);
        obj.setBytes(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
        var nested = new NestedStructure();
        nested.setInnerStr("inner str");
        nested.setList(List.of(1, 2, 3));
        obj.setNested(nested);

        var ser = new SparrowhawkSerializer(obj.size());
        obj.encodeTo(ser);
        System.err.println(HexFormat.of().formatHex(ser.payload()));
        System.err.println();
        new Enhancer(MODEL).enhance(ser.payload(), MODEL.expectShape(ShapeId.from("demo#DemoInput")));
    }
}

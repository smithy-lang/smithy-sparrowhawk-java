package demo;

import java.nio.file.Paths;
import java.util.HexFormat;
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

        var ser = new SparrowhawkSerializer(obj.size());
        obj.encodeTo(ser);

        System.err.println(HexFormat.of().formatHex(ser.payload()));
        System.err.println();
        new Enhancer(MODEL).enhance(ser.payload(), MODEL.expectShape(ShapeId.from("demo#DemoInput")));
    }
}

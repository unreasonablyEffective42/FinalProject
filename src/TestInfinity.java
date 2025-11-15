public class TestInfinity {
    public static void main(String[] args) {
        Parser parser = new Parser("infinity");
        Renderer renderer = new Renderer();
        System.out.println(renderer.compile(parser.parse()));
    }
}

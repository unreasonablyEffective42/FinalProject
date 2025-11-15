public class TestRenderer {
    public static void main(String[] args) {
        Renderer renderer = new Renderer();
        Parser parser = new Parser("sqrt(3/4) + 5/4*cos(x)");
        Expression expr = parser.parse();
        System.out.println(renderer.compile(expr));
    }
}

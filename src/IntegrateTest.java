public class IntegrateTest {
    public static void main(String[] args) {
        Parser parser = new Parser("integrate(sin(x), x, 0, pi)", false, false);
        Renderer renderer = new Renderer();
        Expression expr = parser.parse();
        System.out.println(renderer.compile(expr));
        Parser parser2 = new Parser("integrate(sin(x), x, 0, pi)");
        Expression evaluated = parser2.parse();
        System.out.println(renderer.compile(evaluated));
    }
}

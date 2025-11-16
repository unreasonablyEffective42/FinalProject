public class PolynomialTest {
    public static void main(String[] args) {
        Renderer renderer = new Renderer();

        Parser simpleRoots = new Parser("roots(x^2 - 5x + 6, x)");
        Expression simpleResult = simpleRoots.parse();
        System.out.println("roots(x^2 - 5x + 6, x) => " + renderer.compile(simpleResult));

        Parser complexRoots = new Parser("roots(2x^4 - 4x^3 + x^2 - 2x, x)");
        Expression complexResult = complexRoots.parse();
        System.out.println("roots(2x^4 - 4x^3 + x^2 - 2x, x) => " + renderer.compile(complexResult));

        Parser factorExample = new Parser("factor(2x^4 - 4x^3 + x^2 - 2x, x)");
        Expression factorResult = factorExample.parse();
        System.out.println("factor(2x^4 - 4x^3 + x^2 - 2x, x) => " + renderer.compile(factorResult));
    }
}

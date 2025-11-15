public class PrintExpr {
    public static void main(String[] args) {
        Parser parser = new Parser("5/4cos(x)");
        Expression expr = parser.parse();
        Expression.printExpr(expr);
    }
}

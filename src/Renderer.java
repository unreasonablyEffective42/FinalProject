import java.util.HashMap;
import java.awt.image.BufferedImage;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.scilab.forge.jlatexmath.TeXConstants;

public class Renderer{
    HashMap<Character,String> operators;
    HashMap<Character,String> groupings;

    Renderer(){
        operators = new HashMap<>();
        groupings = new HashMap<>();
        operators.put('+', "%s + %s");
        operators.put('-', "%s - %s");
        operators.put('*', "%s \\cdot %s");
        operators.put('/', "\\frac {%s} {%s}");
        operators.put('^', "%s^{%s}");
        groupings.put('p', "(%s)");
    }

    public String compile(Expression expr){
        return compile(expr, null);
    }

    private String compile(Expression expr, Token parent){
        if (expr == null || expr.getRoot() == null) {
            return "";
        }
        String result;
        switch(expr.getRoot().type()){
            case (Types.OPERATOR):
                result = this.operators.get(expr.getRoot().value());
                return String.format(result,
                        compile(expr.getLeft(), expr.getRoot()),
                        compile(expr.getRight(), expr.getRoot()));
            case (Types.NUMBER):
                return renderNumber(expr.getRoot().value());
            case (Types.SYMBOL):
                return expr.getRoot().value().toString();
            case (Types.PARENTHESES):
                return renderParentheses(expr, parent);
            case (Types.GROUPING):
                return renderGrouping(expr, parent);
            case (Types.PREFIX):
                return renderPrefix(expr, parent);
            default:
                throw new RuntimeException("invalid expression type");
        }
    }

    private String renderNumber(Object value) {
        if (value instanceof Number number) {
            double numeric = Number.toDouble(number);
            if (Double.isInfinite(numeric)) {
                return "\\infty";
            }
            if (approxEquals(number, Number.constantPi())) {
                return "\\pi";
            }
            if (approxEquals(number, Number.constantE())) {
                return "\\mathrm{e}";
            }
            if (approxEquals(number, Number.constantTau())) {
                return "2\\pi";
            }
            if (Double.isInfinite(Number.toDouble(number))) {
                return "\\infty";
            }
            return number.toString();
        }
        return value.toString();
    }

    private boolean approxEquals(Number a, Number constant) {
        return Math.abs(Number.toDouble(a) - Number.toDouble(constant)) < 1e-9;
    }

    private String renderPrefix(Expression expr, Token parent) {
        Object value = expr.getRoot().value();
        Expression operand = expr.getRight();
        String operandStr = operand != null ? compile(operand, expr.getRoot()) : "";

        if (value instanceof LimitInfo limitInfo) {
            String approaching = compileInline(limitInfo.getApproaching());
            String target = compileInline(limitInfo.getTarget());
            return String.format("\\lim_{%s \\to %s} %s", approaching, target, operandStr);
        }

        String prefix = value.toString();
        if (prefix.contains("%s")) {
            return String.format(prefix, operandStr);
        }
        return String.format("%s %s", prefix, operandStr);
    }

    private String compileInline(String src) {
        Parser parser = new Parser(src);
        Expression expr = parser.parse();
        return compile(expr, null);
    }

    private String renderGrouping(Expression expr, Token parent) {
        Object value = expr.getRoot().value();
        if ("int".equals(value)) {
            return renderIntegral(expr);
        }
        String result = "\\%s{%s}";
        return String.format(result, value, compile(expr.getRight(), expr.getRoot()));
    }

    private String renderIntegral(Expression expr) {
        Expression params = expr.getLeft();
        Expression integrand = expr.getRight();
        String integrandStr = integrand != null ? compile(integrand) : "";

        Expression symbolWrapper = params;
        Expression lowerWrapper = symbolWrapper != null ? symbolWrapper.getRight() : null;
        Expression upperWrapper = lowerWrapper != null ? lowerWrapper.getRight() : null;

        if (symbolWrapper == null) {
            throw new RuntimeException("Integral missing variable of integration");
        }

        String symbol = compileParam(symbolWrapper);

        if (lowerWrapper == null && upperWrapper == null) {
            return String.format("\\int %s d%s", integrandStr, symbol);
        }

        if (lowerWrapper == null || upperWrapper == null) {
            throw new RuntimeException("Definite integral requires both bounds");
        }

        String lower = compileParam(lowerWrapper);
        String upper = compileParam(upperWrapper);
        return String.format("\\int_{%s}^{%s} %s d%s", lower, upper, integrandStr, symbol);
    }

    private String compileParam(Expression wrapper) {
        if (wrapper == null || wrapper.getLeft() == null) {
            return "";
        }
        return compile(wrapper.getLeft(), wrapper.getRoot());
    }

    private String renderParentheses(Expression expr, Token parent) {
        Expression inner = expr.getRight();
        if (inner == null || inner.getRoot() == null) {
            return "";
        }
        Types innerType = (Types) inner.getRoot().type();
        if (innerType == Types.NUMBER
                || innerType == Types.SYMBOL
                || innerType == Types.GROUPING
                || innerType == Types.PREFIX
                || isUnaryOperator(inner)
                || shouldRelaxForParent(parent)) {
            return compile(inner, expr.getRoot());
        }
        return String.format("(%s)", compile(inner, expr.getRoot()));
    }

    private boolean isUnaryOperator(Expression expr) {
        if (expr.getRoot().type() != Types.OPERATOR) {
            return false;
        }
        Expression left = expr.getLeft();
        Expression right = expr.getRight();
        return left == null && right != null;
    }

    private boolean shouldRelaxForParent(Token parent) {
        if (parent == null) {
            return false;
        }
        if (parent.type() == Types.OPERATOR) {
            Object value = parent.value();
            if (value instanceof Character) {
                char op = (Character) value;
                return op == '/';
            }
        }
        return false;
    }

    public TeXFormula toTex(Expression expr){
        return new TeXFormula(this.compile(expr));
    }

}

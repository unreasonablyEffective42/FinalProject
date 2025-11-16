import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                return renderOperator(expr);
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
                return "\\tau";
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
        if ("integrate".equals(value)) {
            return renderNumericIntegral(expr);
        }
        if ("dd".equals(value)) {
            return renderDerivative(expr);
        }
        if ("roots".equals(value)) {
            return renderRootsRequest(expr);
        }
        if ("rootsResult".equals(value)) {
            return renderRootsResult(expr);
        }
        if ("factor".equals(value)) {
            return renderFactorRequest(expr);
        }
        if ("factorResult".equals(value)) {
            return renderFactorResult(expr);
        }
        String argument = compile(expr.getRight(), expr.getRoot());
        return String.format("\\%s\\left(%s\\right)", value, argument);
    }

    private String renderOperator(Expression expr) {
        char symbol = (Character) expr.getRoot().value();
        Expression left = expr.getLeft();
        Expression right = expr.getRight();
        String leftStr = compile(left, expr.getRoot());
        String rightStr = compile(right, expr.getRoot());

        if (symbol == '*') {
            String rationalProduct = renderRationalProduct(expr, left, right);
            if (rationalProduct != null) {
                return rationalProduct;
            }
        }

        if (symbol == '*' && usesImplicitMultiplication(left, right)) {
            return leftStr + rightStr;
        }

        String format = this.operators.get(symbol);
        return String.format(format, leftStr, rightStr);
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

    private String renderDerivative(Expression expr) {
        Expression innerExpr = expr.getLeft();
        Expression variable = expr.getRight();
        String variableName = variable != null ? compile(variable, expr.getRoot()) : "";
        String inner = innerExpr != null ? compile(innerExpr, expr.getRoot()) : "";
        return String.format("\\frac{d}{d%s} \\left(%s\\right)", variableName, inner);
    }

    private String renderNumericIntegral(Expression expr) {
        Expression params = expr.getRight();
        if (params == null || params.getLeft() == null || params.getRight() == null) {
            return "integrate(...)";
        }
        String variable = compile(params.getLeft(), expr.getRoot());
        Expression bounds = params.getRight();
        Expression lower = bounds.getLeft();
        Expression upper = bounds.getRight();
        String lowerStr = compile(lower, expr.getRoot());
        String upperStr = compile(upper, expr.getRoot());
        String integrand = compile(expr.getLeft(), expr.getRoot());
        return String.format("\\int_{%s}^{%s} %s d%s", lowerStr, upperStr, integrand, variable);
    }

    private String renderRootsRequest(Expression expr) {
        String polynomial = expr.getLeft() != null ? compile(expr.getLeft(), expr.getRoot()) : "";
        String variable = expr.getRight() != null ? compile(expr.getRight(), expr.getRoot()) : "";
        return String.format("\\operatorname{roots}\\left(%s, %s\\right)", polynomial, variable);
    }

    private String renderRootsResult(Expression expr) {
        List<String> parts = new ArrayList<>();
        Expression current = expr.getLeft();
        while (current != null) {
            if (current.getLeft() != null) {
                parts.add(compile(current.getLeft(), expr.getRoot()));
            }
            current = current.getRight();
        }
        String joined = String.join(", ", parts);
        return String.format("\\left\\{%s\\right\\}", joined);
    }

    private String renderFactorRequest(Expression expr) {
        String polynomial = expr.getLeft() != null ? compile(expr.getLeft(), expr.getRoot()) : "";
        String variable = expr.getRight() != null ? compile(expr.getRight(), expr.getRoot()) : "";
        return String.format("\\operatorname{factor}\\left(%s, %s\\right)", polynomial, variable);
    }

    private String renderFactorResult(Expression expr) {
        List<String> parts = new ArrayList<>();
        Expression current = expr.getLeft();
        while (current != null) {
            if (current.getLeft() != null) {
                parts.add(compile(current.getLeft(), expr.getRoot()));
            }
            current = current.getRight();
        }
        String joined = String.join(", ", parts);
        return String.format("\\left\\{%s\\right\\}", joined);
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

    private boolean usesImplicitMultiplication(Expression left, Expression right) {
        return isNumericNode(left) && !isNumericNode(right);
    }

    private String renderRationalProduct(Expression expr, Expression left, Expression right) {
        RationalParts parts = null;
        Expression other = null;
        if (isRationalNumber(left)) {
            parts = splitRational((Number) left.getRoot().value());
            other = right;
        } else if (isRationalNumber(right)) {
            parts = splitRational((Number) right.getRoot().value());
            other = left;
        }
        if (parts == null || other == null || isOne(parts.denominator)) {
            return null;
        }
        String numeratorStr = compile(other, expr.getRoot());
        if (!isOne(parts.numerator)) {
            if (isNegativeOne(parts.numerator)) {
                numeratorStr = "-" + numeratorStr;
            } else {
                String coeffStr = renderNumber(parts.numerator);
                numeratorStr = String.format("%s \\cdot %s", coeffStr, numeratorStr);
            }
        }
        String denominatorStr = renderNumber(parts.denominator);
        return String.format("\\frac{%s}{%s}", numeratorStr, denominatorStr);
    }

    private boolean isNumericNode(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.NUMBER;
    }

    private boolean isRationalNumber(Expression expr) {
        if (expr == null || expr.getRoot() == null || expr.getRoot().type() != Types.NUMBER) {
            return false;
        }
        Number number = (Number) expr.getRoot().value();
        return number.type == Number.Type.RATIONAL || number.type == Number.Type.BIGRATIONAL;
    }

    private RationalParts splitRational(Number number) {
        switch (number.type) {
            case RATIONAL:
                return new RationalParts(
                        Number.integer(number.num),
                        Number.integer(number.den));
            case BIGRATIONAL:
                return new RationalParts(
                        Number.integer(number.bigNum),
                        Number.integer(number.bigDen));
            default:
                return null;
        }
    }

    private boolean isOne(Number number) {
        return Number.numericEquals(number, Number.integer(1));
    }

    private boolean isNegativeOne(Number number) {
        return Number.numericEquals(number, Number.integer(-1));
    }

    public TeXFormula toTex(Expression expr){
        return new TeXFormula(this.compile(expr));
    }

    private static class RationalParts {
        final Number numerator;
        final Number denominator;

        RationalParts(Number numerator, Number denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }

}

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Walks an {@link Expression} tree and attempts to collect coefficients for a
 * specified variable. Only exact numeric coefficients (integers/rationals) are
 * supported; any other structure causes extraction to return {@code null}.
 */
public class PolynomialExtractor {
    private static final Number ONE = Number.integer(1);
    private static final Number ZERO = Number.integer(0);

    public Polynomial extract(Expression expr, String variable) {
        Polynomial result = extractInternal(expr, variable);
        if (result == null) {
            return null;
        }
        return result;
    }

    private Polynomial extractInternal(Expression expr, String variable) {
        if (expr == null || expr.getRoot() == null) {
            return Polynomial.zero();
        }
        Token root = expr.getRoot();
        switch ((Types) root.type()) {
            case NUMBER:
                Number numeric = (Number) root.value();
                if (!isExactNumber(numeric)) {
                    return null;
                }
                return new Polynomial(Collections.singletonList(numeric));
            case SYMBOL:
                String name = root.value().toString();
                if (name.equals(variable)) {
                    return monomial(1);
                }
                return null;
            case OPERATOR:
                char op = (char) root.value();
                return switch (op) {
                    case '+' -> combine(expr.left, expr.right, variable, Operation.ADD);
                    case '-' -> handleSubtract(expr, variable);
                    case '*' -> combine(expr.left, expr.right, variable, Operation.MULTIPLY);
                    case '^' -> handleExponent(expr, variable);
                    default -> null;
                };
            case PARENTHESES:
                return extractInternal(expr.getRight(), variable);
            default:
                return null;
        }
    }

    private Polynomial handleSubtract(Expression expr, String variable) {
        if (expr.left == null) {
            Polynomial inner = extractInternal(expr.right, variable);
            return inner == null ? null : inner.scale(Number.integer(-1));
        }
        Polynomial left = extractInternal(expr.left, variable);
        Polynomial right = extractInternal(expr.right, variable);
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right);
    }

    private Polynomial handleExponent(Expression expr, String variable) {
        Polynomial base = extractInternal(expr.left, variable);
        if (base == null) {
            return null;
        }
        Integer exponent = extractExponent(expr.right);
        if (exponent == null || exponent < 0) {
            return null;
        }
        return base.pow(exponent);
    }

    private Polynomial combine(Expression leftExpr, Expression rightExpr, String variable, Operation op) {
        Polynomial left = extractInternal(leftExpr, variable);
        Polynomial right = extractInternal(rightExpr, variable);
        if (left == null || right == null) {
            return null;
        }
        return op == Operation.ADD ? left.add(right) : left.multiply(right);
    }

    private Integer extractExponent(Expression expr) {
        if (expr == null || expr.getRoot() == null || expr.getRoot().type() != Types.NUMBER) {
            return null;
        }
        Number number = (Number) expr.getRoot().value();
        if (!isExactNumber(number)) {
            return null;
        }
        switch (number.type) {
            case INT:
                return (int) number.intVal;
            case BIGINT:
                try {
                    return number.bigVal.intValueExact();
                } catch (ArithmeticException ex) {
                    return null;
                }
            default:
                return null;
        }
    }

    private Polynomial monomial(int power) {
        List<Number> coeffs = new ArrayList<>();
        for (int i = 0; i <= power; i++) {
            coeffs.add(i == power ? ONE : ZERO);
        }
        return new Polynomial(coeffs);
    }

    private boolean isExactNumber(Number number) {
        return switch (number.type) {
            case INT, BIGINT, RATIONAL, BIGRATIONAL -> true;
            default -> false;
        };
    }

    private enum Operation {
        ADD,
        MULTIPLY
    }
}

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Evaluator {
    private final List<RewriteRule> rules = new ArrayList<>();

    public Evaluator() {
        registerDefaultRules();
    }

    public Expression simplify(Expression expr) {
        Expression current = cloneExpression(expr);
        boolean changed;
        do {
            RewriteResult result = rewrite(current);
            current = result.expression;
            changed = result.changed;
        } while (changed);
        return current;
    }

    private RewriteResult rewrite(Expression expr) {
        if (expr == null || expr.getRoot() == null) {
            return new RewriteResult(expr, false);
        }

        if (isSqrtExpression(expr)) {
            Expression simplifiedSqrt = simplifySqrtExpression(expr);
            if (simplifiedSqrt != null) {
                return new RewriteResult(simplifiedSqrt, true);
            }
        }
        if (expr.root.type() == Types.OPERATOR && expr.root.value() instanceof Character) {
            Expression left = expr.left;
            Expression right = expr.right;
            if (isNumericExpression(left) && isNumericExpression(right)) {
                Expression folded = foldBinary(expr.root, left, right);
                if (folded != null) {
                    return new RewriteResult(folded, true);
                }
            }

            if ((Character) expr.root.value() == '/'
                    && expr.right != null
                    && containsSqrtFactor(expr.right)) {
                Expression rationalized = rationalizeFraction(expr);
                if (rationalized != null) {
                    return new RewriteResult(rationalized, true);
                }
            }
        }

        for (RewriteRule rule : rules) {
            Map<String, Expression> bindings = new HashMap<>();
            if (rule.pattern.matches(expr, bindings, this)) {
                Expression replaced = rule.build(bindings);
                return new RewriteResult(replaced, true);
            }
        }

        boolean modified = false;
        Expression newLeft = expr.left;
        Expression newRight = expr.right;

        if (expr.left != null) {
            RewriteResult leftResult = rewrite(expr.left);
            if (leftResult.changed) {
                newLeft = leftResult.expression;
                modified = true;
            }
        }

        if (expr.right != null) {
            RewriteResult rightResult = rewrite(expr.right);
            if (rightResult.changed) {
                newRight = rightResult.expression;
                modified = true;
            }
        }

        if (modified) {
            Expression updated = new Expression(expr.root);
            updated.left = newLeft;
            updated.right = newRight;
            return new RewriteResult(updated, true);
        }

        return new RewriteResult(expr, false);
    }

    private void registerDefaultRules() {
        rules.add(new RewriteRule(
                PatternNode.operator('+', PatternNode.placeholder("a"), PatternNode.number(0)),
                PatternNode.placeholder("a")));

        rules.add(new RewriteRule(
                PatternNode.operator('+', PatternNode.number(0), PatternNode.placeholder("a")),
                PatternNode.placeholder("a")));

        rules.add(new RewriteRule(
                PatternNode.operator('-', PatternNode.placeholder("a"), PatternNode.number(0)),
                PatternNode.placeholder("a")));

        rules.add(new RewriteRule(
                PatternNode.operator('*', PatternNode.placeholder("a"), PatternNode.number(1)),
                PatternNode.placeholder("a")));

        rules.add(new RewriteRule(
                PatternNode.operator('*', PatternNode.number(1), PatternNode.placeholder("a")),
                PatternNode.placeholder("a")));

        rules.add(new RewriteRule(
                PatternNode.operator('*', PatternNode.placeholder("a"), PatternNode.number(0)),
                PatternNode.number(0)));

        rules.add(new RewriteRule(
                PatternNode.operator('*', PatternNode.number(0), PatternNode.placeholder("a")),
                PatternNode.number(0)));

        rules.add(new RewriteRule(
                PatternNode.operator('/', PatternNode.placeholder("a"), PatternNode.number(1)),
                PatternNode.placeholder("a")));
    }

    private Expression cloneExpression(Expression expr) {
        if (expr == null) {
            return null;
        }
        Expression clone = new Expression(expr.root);
        clone.left = cloneExpression(expr.left);
        clone.right = cloneExpression(expr.right);
        return clone;
    }

    private boolean structurallyEqual(Expression a, Expression b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.root == null || b.root == null) {
            return a.root == b.root;
        }
        if (!a.root.type().equals(b.root.type())) {
            return false;
        }
        if (!valueEquals(a.root.value(), b.root.value())) {
            return false;
        }
        return structurallyEqual(a.left, b.left) && structurallyEqual(a.right, b.right);
    }

    private boolean valueEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number && b instanceof Number) {
            return Number.numericEquals((Number) a, (Number) b);
        }
        return a.equals(b);
    }

    private boolean isSqrtExpression(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.GROUPING
                && "sqrt".equals(expr.getRoot().value());
    }

    private Expression simplifySqrtExpression(Expression expr) {
        Expression inner = expr.getRight();
        if (!isNumericExpression(inner)) {
            return null;
        }
        Number number = (Number) inner.getRoot().value();
        switch (number.type) {
            case INT:
                if (number.intVal < 0) {
                    return null;
                }
                return buildSqrtFromLong(number.intVal);
            case RATIONAL:
                if (number.num < 0) {
                    return null;
                }
                return buildSqrtFromRational(number.num, number.den);
            default:
                return null;
        }
    }

    private boolean isNumericExpression(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.NUMBER
                && expr.getRoot().value() instanceof Number;
    }

    private Expression foldBinary(Token operator, Expression left, Expression right) {
        char op = (char) operator.value();
        Number lhs = (Number) left.getRoot().value();
        Number rhs = (Number) right.getRoot().value();
        Number result = null;
        switch (op) {
            case '+':
                result = addNumbers(lhs, rhs);
                break;
            case '-':
                result = subtractNumbers(lhs, rhs);
                break;
            case '*':
                result = multiplyNumbers(lhs, rhs);
                break;
            case '/':
                try {
                    result = Number.rational(lhs, rhs);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
                break;
            case '^':
                result = powNumbers(lhs, rhs);
                break;
            default:
                return null;
        }

        if (result == null) {
            return null;
        }
        return new Expression(new Token(Types.NUMBER, result));
    }

    private Number addNumbers(Number a, Number b) {
        if (isExact(a) && isExact(b)) {
            return sumExact(a, b);
        }
        return Number.real(Number.toDouble(a) + Number.toDouble(b));
    }

    private Number subtractNumbers(Number a, Number b) {
        if (isExact(a) && isExact(b)) {
            return sumExact(a, negate(b));
        }
        return Number.real(Number.toDouble(a) - Number.toDouble(b));
    }

    private Number multiplyNumbers(Number a, Number b) {
        if (isExact(a) && isExact(b)) {
            Number numerator = Number.rational(a, Number.integer(1));
            Number denominator = Number.rational(b, Number.integer(1));
            return Number.rational(multExact(a, b), Number.integer(1));
        }
        return Number.real(Number.toDouble(a) * Number.toDouble(b));
    }

    private Number powNumbers(Number base, Number exponent) {
        if (!isExact(exponent)) {
            return Number.real(Math.pow(Number.toDouble(base), Number.toDouble(exponent)));
        }
        Number exactExp = exponent;
        if (exactExp.type == Number.Type.RATIONAL || exactExp.type == Number.Type.BIGRATIONAL) {
            return null;
        }
        int expInt;
        if (exactExp.type == Number.Type.INT) {
            expInt = (int) exactExp.intVal;
        } else if (exactExp.type == Number.Type.BIGINT) {
            try {
                expInt = exactExp.bigVal.intValueExact();
            } catch (ArithmeticException ex) {
                return null;
            }
        } else {
            return null;
        }

        Number result = Number.integer(1);
        Number baseCopy = base;
        int exponentValue = Math.abs(expInt);
        for (int i = 0; i < exponentValue; i++) {
                result = multExact(result, baseCopy);
        }
        if (expInt < 0) {
            result = Number.rational(Number.integer(1), result);
        }
        return result;
    }

    private boolean isExact(Number number) {
        return switch (number.type) {
            case INT, BIGINT, RATIONAL, BIGRATIONAL -> true;
            default -> false;
        };
    }

    private Number sumExact(Number a, Number b) {
        Number.Type typeA = a.type;
        Number.Type typeB = b.type;
        if (typeA == Number.Type.INT && typeB == Number.Type.INT) {
            long sum = a.intVal + b.intVal;
            if ((sum ^ a.intVal) >= 0 || (sum ^ b.intVal) >= 0) {
                return Number.integer(sum);
            }
            return Number.integer(BigInteger.valueOf(a.intVal).add(BigInteger.valueOf(b.intVal)));
        }
        BigInteger[] fracA = toBigFraction(a);
        BigInteger[] fracB = toBigFraction(b);
        BigInteger numerator = fracA[0].multiply(fracB[1]).add(fracB[0].multiply(fracA[1]));
        BigInteger denominator = fracA[1].multiply(fracB[1]);
        return Number.rational(numerator, denominator);
    }

    private Number multExact(Number a, Number b) {
        BigInteger[] fracA = toBigFraction(a);
        BigInteger[] fracB = toBigFraction(b);
        return Number.rational(fracA[0].multiply(fracB[0]), fracA[1].multiply(fracB[1]));
    }

    private Number negate(Number n) {
        if (n.type == Number.Type.INT) {
            return Number.integer(-n.intVal);
        }
        if (n.type == Number.Type.BIGINT) {
            return Number.integer(n.bigVal.negate());
        }
        if (n.type == Number.Type.RATIONAL) {
            return Number.rational(-n.num, n.den);
        }
        if (n.type == Number.Type.BIGRATIONAL) {
            return Number.rational(n.bigNum.negate(), n.bigDen);
        }
        return Number.real(-Number.toDouble(n));
    }

    private BigInteger[] toBigFraction(Number n) {
        Number.Type type = n.type;
        switch (type) {
            case INT:
                return new BigInteger[]{BigInteger.valueOf(n.intVal), BigInteger.ONE};
            case BIGINT:
                return new BigInteger[]{n.bigVal, BigInteger.ONE};
            case RATIONAL:
                return new BigInteger[]{BigInteger.valueOf(n.num), BigInteger.valueOf(n.den)};
            case BIGRATIONAL:
                return new BigInteger[]{n.bigNum, n.bigDen};
            default:
                throw new IllegalArgumentException("Expected exact number, got " + type);
        }
    }

    private Expression buildSqrtFromLong(long value) {
        if (value == 0) {
            return new Expression(new Token(Types.NUMBER, Number.integer(0)));
        }
        if (value == 1) {
            return new Expression(new Token(Types.NUMBER, Number.integer(1)));
        }

        SqrtComponents components = factorSquareComponents(value);
        if (components.inside == 1) {
            return buildNumberExpression(Number.integer(components.outside));
        }
        if (components.outside == 1 && components.inside == value) {
            return null;
        }

        Expression insideExpr = buildNumberExpression(Number.integer(components.inside));
        Expression sqrtExpr = buildSqrtExpression(insideExpr);
        if (components.outside == 1) {
            return sqrtExpr;
        }
        return buildProductExpression(
                buildNumberExpression(Number.integer(components.outside)),
                sqrtExpr);
    }

    private Expression buildSqrtFromRational(long numerator, long denominator) {
        SqrtComponents numComp = factorSquareComponents(numerator);
        SqrtComponents denComp = factorSquareComponents(denominator);

        Long coefficientDen = safeMultiply(denComp.outside, denComp.inside);
        Long insideProduct = safeMultiply(numComp.inside, denComp.inside);
        if (coefficientDen == null || insideProduct == null) {
            return null;
        }

        Number coefficient = Number.rational(numComp.outside, coefficientDen);
        if (insideProduct == 1) {
            return buildNumberExpression(coefficient);
        }

        Expression sqrtExpr = buildSqrtFromLong(insideProduct);
        if (sqrtExpr == null) {
            sqrtExpr = buildSqrtExpression(buildNumberExpression(Number.integer(insideProduct)));
        }

        if (isOne(coefficient)) {
            return sqrtExpr;
        }

        if (isSquareRootWithNumber(sqrtExpr)
                && coefficient.type == Number.Type.RATIONAL) {
            Expression numeratorExpr = buildProductExpression(
                    buildNumberExpression(Number.integer(coefficient.num)),
                    cloneExpression(sqrtExpr));
            Expression denominatorExpr = buildNumberExpression(Number.integer(coefficient.den));

            Expression fraction = new Expression(new Token(Types.OPERATOR, '/'));
            fraction.left = numeratorExpr;
            fraction.right = denominatorExpr;
            return fraction;
        }

        return buildProductExpression(buildNumberExpression(coefficient), sqrtExpr);
    }

    private boolean isSquareRootWithNumber(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.GROUPING
                && "sqrt".equals(expr.getRoot().value())
                && expr.getRight() != null
                && expr.getRight().getRoot() != null
                && expr.getRight().getRoot().type() == Types.NUMBER;
    }

    private boolean containsSqrtFactor(Expression expr) {
        Expression target = unwrapParentheses(expr);
        return findSqrtFactor(target) != null;
    }

    private Expression rationalizeFraction(Expression expr) {
        Expression denominatorOriginal = expr.right;
        Expression denominatorUnwrapped = unwrapParentheses(denominatorOriginal);
        SqrtFactor factor = findSqrtFactor(denominatorUnwrapped);
        if (factor == null || expr.left == null) {
            return null;
        }
        Expression numerator = buildProductExpression(
                cloneExpression(expr.left),
                cloneExpression(factor.sqrtExpr));

        Expression denominator = cloneExpression(factor.radicalInner);
        if (factor.coefficient != null) {
            denominator = buildProductExpression(
                    cloneExpression(factor.coefficient),
                    denominator);
        }

        Expression fraction = new Expression(new Token(Types.OPERATOR, '/'));
        fraction.left = numerator;
        fraction.right = denominator;
        return fraction;
    }

    private SqrtFactor findSqrtFactor(Expression expr) {
        if (expr == null || expr.getRoot() == null) {
            return null;
        }

        if (isSquareRootExpression(expr)) {
            return new SqrtFactor(null, expr, expr.right);
        }

        if (expr.getRoot().type() == Types.OPERATOR
                && expr.getRoot().value() instanceof Character
                && (Character) expr.getRoot().value() == '*') {
            if (isSquareRootExpression(expr.left)) {
                return new SqrtFactor(expr.right, expr.left, expr.left.right);
            }
            if (isSquareRootExpression(expr.right)) {
                return new SqrtFactor(expr.left, expr.right, expr.right.right);
            }
        }

        return null;
    }

    private Expression unwrapParentheses(Expression expr) {
        Expression current = expr;
        while (current != null
                && current.getRoot() != null
                && current.getRoot().type() == Types.PARENTHESES
                && current.getRight() != null) {
            current = current.getRight();
        }
        return current;
    }

    private boolean isSquareRootExpression(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.GROUPING
                && "sqrt".equals(expr.getRoot().value())
                && expr.getRight() != null;
    }

    private Expression buildNumberExpression(Number number) {
        return new Expression(new Token(Types.NUMBER, number));
    }

    private Expression buildSqrtExpression(Expression inner) {
        Expression sqrt = new Expression(new Token(Types.GROUPING, "sqrt"));
        sqrt.right = inner;
        return sqrt;
    }

    private Expression buildProductExpression(Expression left, Expression right) {
        Expression product = new Expression(new Token(Types.OPERATOR, '*'));
        product.left = left;
        product.right = right;
        return product;
    }

    private boolean isOne(Number number) {
        return Number.numericEquals(number, Number.integer(1));
    }

    private SqrtComponents factorSquareComponents(long value) {
        long outside = 1;
        long inside = 1;
        long remaining = value;
        for (long factor = 2; factor * factor <= remaining; factor++) {
            int count = 0;
            while (remaining % factor == 0) {
                remaining /= factor;
                count++;
            }
            if (count > 0) {
                for (int i = 0; i < count / 2; i++) {
                    outside *= factor;
                }
                if ((count % 2) == 1) {
                    inside *= factor;
                }
            }
        }
        inside *= remaining;
        return new SqrtComponents(outside, inside);
    }

    private Long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static class SqrtComponents {
        final long outside;
        final long inside;

        SqrtComponents(long outside, long inside) {
            this.outside = outside;
            this.inside = inside;
        }
    }

    private static class SqrtFactor {
        final Expression coefficient;
        final Expression sqrtExpr;
        final Expression radicalInner;

        SqrtFactor(Expression coefficient, Expression sqrtExpr, Expression radicalInner) {
            this.coefficient = coefficient;
            this.sqrtExpr = sqrtExpr;
            this.radicalInner = radicalInner;
        }
    }

    private static class RewriteResult {
        final Expression expression;
        final boolean changed;

        RewriteResult(Expression expression, boolean changed) {
            this.expression = expression;
            this.changed = changed;
        }
    }

    private static class PatternNode {
        final Token token;
        final PatternNode left;
        final PatternNode right;
        final String placeholder;

        private PatternNode(Token token, PatternNode left, PatternNode right, String placeholder) {
            this.token = token;
            this.left = left;
            this.right = right;
            this.placeholder = placeholder;
        }

        static PatternNode placeholder(String name) {
            return new PatternNode(null, null, null, name);
        }

        static PatternNode operator(char symbol, PatternNode left, PatternNode right) {
            return new PatternNode(new Token(Types.OPERATOR, symbol), left, right, null);
        }

        static PatternNode number(int value) {
            return new PatternNode(new Token(Types.NUMBER, Number.integer(value)), null, null, null);
        }

        boolean isPlaceholder() {
            return placeholder != null;
        }

        boolean matches(Expression expr, Map<String, Expression> bindings, Evaluator evaluator) {
            if (isPlaceholder()) {
                Expression bound = bindings.get(placeholder);
                if (bound == null) {
                    bindings.put(placeholder, expr);
                    return true;
                }
                return evaluator.structurallyEqual(bound, expr);
            }

            if (expr == null || expr.root == null) {
                return false;
            }

            if (token != null) {
                if (!expr.root.type().equals(token.type())) {
                    return false;
                }
                Object tokenValue = token.value();
                if (tokenValue != null && !evaluator.valueEquals(tokenValue, expr.root.value())) {
                    return false;
                }
            }

            if (left != null) {
                if (!left.matches(expr.left, bindings, evaluator)) {
                    return false;
                }
            } else if (expr.left != null) {
                return false;
            }

            if (right != null) {
                if (!right.matches(expr.right, bindings, evaluator)) {
                    return false;
                }
            } else if (expr.right != null) {
                return false;
            }

            return true;
        }
    }

    private static class RewriteRule {
        final PatternNode pattern;
        final PatternNode replacement;

        RewriteRule(PatternNode pattern, PatternNode replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }

        Expression build(Map<String, Expression> bindings) {
            return substitute(replacement, bindings);
        }

        private Expression substitute(PatternNode node, Map<String, Expression> bindings) {
            if (node == null) {
                return null;
            }
            if (node.isPlaceholder()) {
                Expression bound = bindings.get(node.placeholder);
                return bound == null ? null : cloneTree(bound);
            }
            Expression leftExpr = substitute(node.left, bindings);
            Expression rightExpr = substitute(node.right, bindings);
            Expression expr = new Expression(node.token);
            expr.left = leftExpr;
            expr.right = rightExpr;
            return expr;
        }

        private Expression cloneTree(Expression expr) {
            if (expr == null) {
                return null;
            }
            Expression clone = new Expression(expr.root);
            clone.left = cloneTree(expr.left);
            clone.right = cloneTree(expr.right);
            return clone;
        }
    }
}

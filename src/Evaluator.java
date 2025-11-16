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

    public Expression normalizeProduct(Expression expr) {
        if (!isMultiplication(expr)) {
            return expr;
        }
        NumericFactor factor = extractNumericFactor(expr);
        if (factor.coefficient != null && factor.remainder != null) {
            Expression coefficientExpr = buildNumberExpression(factor.coefficient);
            return buildProductExpression(coefficientExpr, factor.remainder);
        }
        return expr;
    }

    public List<Expression> roots(Expression expr, String variable) {
        PolynomialExtractor extractor = new PolynomialExtractor();
        Polynomial polynomial = extractor.extract(expr, variable);
        if (polynomial == null || polynomial.degree() < 1) {
            throw new IllegalArgumentException("Expression is not a polynomial in " + variable);
        }
        PolynomialSolver solver = new PolynomialSolver();
        return solver.solve(polynomial);
    }

    public List<Expression> factor(Expression expr, String variable) {
        PolynomialExtractor extractor = new PolynomialExtractor();
        Polynomial polynomial = extractor.extract(expr, variable);
        if (polynomial == null || polynomial.degree() < 1) {
            throw new IllegalArgumentException("Expression is not a polynomial in " + variable);
        }
        PolynomialFactorizer factorizer = new PolynomialFactorizer();
        return factorizer.factor(polynomial, variable);
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

        if (expr.root.type() == Types.GROUPING
                && ( "sin".equals(expr.root.value())
                || "cos".equals(expr.root.value())
                || "tan".equals(expr.root.value()))) {
            Expression trig = simplifyTrig(expr);
            if (trig != null) {
                return new RewriteResult(trig, true);
            }
        }
        if (expr.root.type() == Types.OPERATOR && expr.root.value() instanceof Character) {
            Expression left = expr.left;
            Expression right = expr.right;
            char opChar = (Character) expr.root.value();
            if (opChar == '-' && left == null && isNumericExpression(right)) {
                Number value = (Number) right.getRoot().value();
                Expression negated = buildNumberExpression(negate(value));
                return new RewriteResult(negated, true);
            }
            if (opChar == '-' && left != null && isNumericExpression(left)) {
                Number leftValue = (Number) left.getRoot().value();
                if (isZero(leftValue)) {
                    Expression negated = negateExpression(cloneExpression(right));
                    return new RewriteResult(negated, true);
                }
            }
            if (isNumericExpression(left) && isNumericExpression(right)) {
                Expression folded = foldBinary(expr.root, left, right);
                if (folded != null) {
                    return new RewriteResult(folded, true);
                }
            }

            if (opChar == '/'
                    && expr.right != null
                    && containsSqrtFactor(expr.right)) {
                Expression rationalized = rationalizeFraction(expr);
                if (rationalized != null) {
                    return new RewriteResult(rationalized, true);
                }
            }

            if (opChar == '/' && expr.right != null && expr.left != null) {
                Expression normalized = reduceFractionCoefficient(expr.left, expr.right);
                if (normalized != null) {
                    return new RewriteResult(normalized, true);
                }
            }

            if (opChar == '*') {
                Expression merged = combineNumericProduct(expr.left, expr.right);
                if (merged != null) {
                    return new RewriteResult(merged, true);
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
        Number number = extractExactNumber(inner);
        if (number == null) {
            return null;
        }
        switch (number.type) {
            case INT:
                if (number.intVal < 0) {
                    return buildImaginarySqrtFromLong(number.intVal);
                }
                return buildSqrtFromLong(number.intVal);
            case BIGINT:
                if (number.bigVal.signum() < 0) {
                    return buildImaginarySqrtFromBigInteger(number.bigVal);
                }
                return buildSqrtFromBigInteger(number.bigVal);
            case RATIONAL:
                if (number.num < 0) {
                    return buildImaginarySqrtFromRational(number.num, number.den);
                }
                return buildSqrtFromRational(number.num, number.den);
            case BIGRATIONAL:
                if (number.bigNum.signum() < 0) {
                    return buildImaginarySqrtFromBigRational(number.bigNum, number.bigDen);
                }
                return buildSqrtFromBigRational(number.bigNum, number.bigDen);
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
            return Number.multiply(a, b);
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

    private Expression buildImaginarySqrtFromLong(long negativeValue) {
        Expression magnitude;
        if (negativeValue == Long.MIN_VALUE) {
            Number positive = Number.integer(BigInteger.valueOf(negativeValue).negate());
            magnitude = buildSqrtExpression(buildNumberExpression(positive));
        } else {
            long positive = Math.abs(negativeValue);
            magnitude = buildSqrtFromLong(positive);
            if (magnitude == null) {
                magnitude = buildSqrtExpression(buildNumberExpression(Number.integer(positive)));
            }
        }
        return multiplyByImaginary(magnitude);
    }

    private Expression buildSqrtFromBigInteger(BigInteger value) {
        try {
            return buildSqrtFromLong(value.longValueExact());
        } catch (ArithmeticException ex) {
            return buildSqrtExpression(buildNumberExpression(Number.integer(value)));
        }
    }

    private Expression buildImaginarySqrtFromBigInteger(BigInteger value) {
        if (value.signum() >= 0) {
            return buildSqrtFromBigInteger(value);
        }
        BigInteger positive = value.negate();
        Expression magnitude = buildSqrtFromBigInteger(positive);
        return multiplyByImaginary(magnitude);
    }

    private Expression buildSqrtFromBigRational(BigInteger numerator, BigInteger denominator) {
        try {
            long num = numerator.longValueExact();
            long den = denominator.longValueExact();
            return buildSqrtFromRational(num, den);
        } catch (ArithmeticException ex) {
            Number rational = Number.rational(numerator, denominator);
            return buildSqrtExpression(buildNumberExpression(rational));
        }
    }

    private Expression buildImaginarySqrtFromBigRational(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() >= 0) {
            return buildSqrtFromBigRational(numerator, denominator);
        }
        Expression magnitude = buildSqrtFromBigRational(numerator.negate(), denominator);
        return multiplyByImaginary(magnitude);
    }

    private Expression buildImaginarySqrtFromRational(long numerator, long denominator) {
        Expression magnitude;
        if (numerator == Long.MIN_VALUE) {
            Number positive = Number.rational(BigInteger.valueOf(numerator).negate(), BigInteger.valueOf(denominator));
            magnitude = buildSqrtExpression(buildNumberExpression(positive));
        } else {
            long positive = Math.abs(numerator);
            magnitude = buildSqrtFromRational(positive, denominator);
            if (magnitude == null) {
                Number positiveRational = Number.rational(positive, denominator);
                magnitude = buildSqrtExpression(buildNumberExpression(positiveRational));
            }
        }
        return multiplyByImaginary(magnitude);
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

    private Expression reduceFractionCoefficient(Expression numerator, Expression denominator) {
        Number denominatorValue = extractExactNumber(denominator);
        if (denominatorValue == null) {
            return null;
        }
        NumericFactor factor = extractNumericFactor(numerator);
        if (factor.coefficient == null) {
            return null;
        }
        Number ratio;
        try {
            ratio = Number.divide(factor.coefficient, denominatorValue);
        } catch (Exception ex) {
            return null;
        }
        if (factor.remainder == null) {
            return buildNumberExpression(ratio);
        }
        NumericFactor remainderFactor = extractNumericFactor(factor.remainder);
        Number totalCoeff = combineCoefficients(ratio, remainderFactor.coefficient);
        Expression coeffExpr = totalCoeff != null ? buildNumberExpression(totalCoeff) : null;
        Expression remainderExpr = remainderFactor.remainder;
        if (remainderExpr == null) {
            return coeffExpr == null ? null : coeffExpr;
        }
        if (coeffExpr == null) {
            return remainderExpr;
        }
        return buildProductExpression(coeffExpr, remainderExpr);
    }

    private Expression combineNumericProduct(Expression left, Expression right) {
        if (left == null || right == null) {
            return null;
        }
        if (isNumericExpression(left) && isMultiplication(right)) {
            Expression merged = mergeNumericIntoProduct(left, right);
            if (merged != null) {
                return merged;
            }
        }
        if (isNumericExpression(right) && isMultiplication(left)) {
            Expression merged = mergeNumericIntoProduct(right, left);
            if (merged != null) {
                return merged;
            }
        }
        return null;
    }

    private boolean isMultiplication(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.OPERATOR
                && expr.getRoot().value() instanceof Character
                && (Character) expr.getRoot().value() == '*';
    }

    private Expression mergeNumericIntoProduct(Expression numericExpr, Expression productExpr) {
        if (!isMultiplication(productExpr)) {
            return null;
        }
        if (isNumericExpression(productExpr.left)) {
            Expression numericProduct = buildProductExpression(
                    cloneExpression(numericExpr),
                    cloneExpression(productExpr.left));
            Expression remainder = cloneExpression(productExpr.right);
            return buildProductExpression(numericProduct, remainder);
        }
        if (isNumericExpression(productExpr.right)) {
            Expression numericProduct = buildProductExpression(
                    cloneExpression(numericExpr),
                    cloneExpression(productExpr.right));
            Expression remainder = cloneExpression(productExpr.left);
            return buildProductExpression(numericProduct, remainder);
        }
        return null;
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

    private String symbolName(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Character) {
            return String.valueOf(value);
        }
        return null;
    }
    private boolean approxEquals(Number a, Number b) {
        return Math.abs(Number.toDouble(a) - Number.toDouble(b)) < 1e-9;
    }

    private boolean isPiNode(Expression expr) {
        if (expr == null || expr.getRoot() == null || expr.getRoot().type() != Types.NUMBER) {
            return false;
        }
        return approxEquals((Number) expr.getRoot().value(), Number.constantPi());
    }

    private boolean isTauNode(Expression expr) {
        if (expr == null || expr.getRoot() == null || expr.getRoot().type() != Types.NUMBER) {
            return false;
        }
        return approxEquals((Number) expr.getRoot().value(), Number.constantTau());
    }

    public Expression differentiate(Expression expr, String variable) {
        Expression derivative = differentiateInternal(expr, variable);
        Expression cleaned = cleanupDerivative(derivative);
        return simplify(cleaned);
    }

    private Expression differentiateInternal(Expression expr, String variable) {
        if (expr == null || expr.getRoot() == null) {
            return zero();
        }

        Types type = (Types) expr.getRoot().type();
        switch (type) {
            case NUMBER:
                return zero();
            case SYMBOL:
                String name = symbolName(expr.getRoot().value());
                if (name != null && name.equals(variable)) {
                    return one();
                }
                return zero();
            case OPERATOR:
                char op = (char) expr.getRoot().value();
                switch (op) {
                    case '+':
                        return addExpr(differentiateInternal(expr.left, variable),
                                differentiateInternal(expr.right, variable));
                    case '-':
                        if (expr.left == null) {
                            return negateExpression(differentiateInternal(expr.right, variable));
                        }
                        return subtractExpr(differentiateInternal(expr.left, variable),
                                differentiateInternal(expr.right, variable));
                    case '*':
                        return addExpr(
                                multiplyExpr(differentiateInternal(expr.left, variable), cloneExpression(expr.right)),
                                multiplyExpr(cloneExpression(expr.left), differentiateInternal(expr.right, variable))
                        );
                    case '/':
                        Expression uPrime = differentiateInternal(expr.left, variable);
                        Expression vPrime = differentiateInternal(expr.right, variable);
                        Expression numerator = subtractExpr(
                                multiplyExpr(uPrime, cloneExpression(expr.right)),
                                multiplyExpr(cloneExpression(expr.left), vPrime));
                        Expression denominator = powExpression(cloneExpression(expr.right),
                                buildNumberExpression(Number.integer(2)));
                        return divideExpr(numerator, denominator);
                    case '^':
                        return differentiatePower(expr, variable);
                    default:
                        return zero();
                }
            case GROUPING:
                String func = (String) expr.getRoot().value();
                Expression inner = expr.right;
                Expression innerPrime = differentiateInternal(inner, variable);
                switch (func) {
                    case "sin":
                        return multiplyExpr(buildGrouping("cos", cloneExpression(inner)), innerPrime);
                    case "cos":
                        return multiplyExpr(negateExpression(buildGrouping("sin", cloneExpression(inner))), innerPrime);
                    case "tan":
                        Expression cosInner = buildGrouping("cos", cloneExpression(inner));
                        Expression secSquared = divideExpr(one(), powExpression(cosInner,
                                buildNumberExpression(Number.integer(2))));
                        return multiplyExpr(secSquared, innerPrime);
                    case "sqrt":
                        Expression denominator = multiplyExpr(
                                buildNumberExpression(Number.integer(2)),
                                buildSqrtExpression(cloneExpression(inner)));
                        return divideExpr(innerPrime, denominator);
                    case "ln":
                        return divideExpr(innerPrime, cloneExpression(inner));
                    default:
                        return zero();
                }
            default:
                return zero();
        }
    }

    private Expression differentiatePower(Expression expr, String variable) {
        Expression base = expr.left;
        Expression exponent = expr.right;
        if (base == null || exponent == null) {
            return zero();
        }

        boolean exponentIsNumber = isNumericExpression(exponent);
        boolean baseIsNumber = isNumericExpression(base);

        Expression basePrime = differentiateInternal(base, variable);
        Expression exponentPrime = differentiateInternal(exponent, variable);

        if (exponentIsNumber) {
            Number n = (Number) exponent.getRoot().value();
            Number newExponent = sumExact(n, Number.integer(-1));
            Expression coefficient = buildNumberExpression(n);
            Expression power = powExpression(cloneExpression(base), buildNumberExpression(newExponent));
            return multiplyExpr(multiplyExpr(coefficient, power), basePrime);
        }

        if (baseIsNumber) {
            Number baseValue = (Number) base.getRoot().value();
            Expression lnBase = buildGrouping("ln", cloneExpression(base));
            Expression basePow = powExpression(cloneExpression(base), cloneExpression(exponent));
            return multiplyExpr(multiplyExpr(basePow, lnBase), exponentPrime);
        }

        Expression term1 = multiplyExpr(exponentPrime, buildGrouping("ln", cloneExpression(base)));
        Expression term2 = multiplyExpr(cloneExpression(exponent),
                divideExpr(basePrime, cloneExpression(base)));
        Expression inner = addExpr(term1, term2);
        Expression basePow = powExpression(cloneExpression(base), cloneExpression(exponent));
        return multiplyExpr(basePow, inner);
    }

    private Expression simplifyTrig(Expression expr) {
        Expression argument = expr.right;
        if (argument == null) {
            return null;
        }
        Fraction multiple = extractPiMultiple(argument);
        Expression result = null;
        String name = (String) expr.getRoot().value();
        if (multiple != null) {
            switch (name) {
                case "sin":
                    result = exactSin(multiple);
                    break;
                case "cos":
                    result = exactCos(multiple);
                    break;
                case "tan":
                    result = exactTan(multiple);
                    break;
            }
        }
        if (result != null) {
            return result;
        }
        Double approx = approximateValue(argument);
        if (approx == null) {
            return null;
        }
        double numeric = approx;
        double trigValue;
        switch (name) {
            case "sin":
                trigValue = Math.sin(numeric);
                break;
            case "cos":
                trigValue = Math.cos(numeric);
                break;
            case "tan":
            default:
                trigValue = Math.tan(numeric);
                break;
        }
        return buildNumberExpression(Number.real((float) trigValue));
    }

    private Double approximateValue(Expression expr) {
        if (expr == null || expr.getRoot() == null) {
            return null;
        }
        Expression target = unwrapParentheses(expr);
        if (target == null || target.getRoot() == null) {
            return null;
        }
        Token root = target.getRoot();
        switch ((Types) root.type()) {
            case NUMBER:
                return Number.toDouble((Number) root.value());
            case OPERATOR:
                char op = (char) root.value();
                Double leftVal = target.left != null ? approximateValue(target.left) : null;
                Double rightVal = target.right != null ? approximateValue(target.right) : null;
                switch (op) {
                    case '+':
                        if (leftVal != null && rightVal != null) return leftVal + rightVal;
                        break;
                    case '-':
                        if (leftVal != null && rightVal != null) return leftVal - rightVal;
                        if (leftVal == null && rightVal != null) return -rightVal;
                        break;
                    case '*':
                        if (leftVal != null && rightVal != null) return leftVal * rightVal;
                        break;
                    case '/':
                        if (leftVal != null && rightVal != null) return leftVal / rightVal;
                        break;
                    case '^':
                        if (leftVal != null && rightVal != null) return Math.pow(leftVal, rightVal);
                        break;
                }
                break;
            case GROUPING:
                String name = (String) root.value();
                if ("sqrt".equals(name)) {
                    Double inner = approximateValue(target.right);
                    return inner != null ? Math.sqrt(inner) : null;
                }
                if ("sin".equals(name) || "cos".equals(name) || "tan".equals(name)) {
                    Double inner = approximateValue(target.right);
                    if (inner == null) return null;
                    switch (name) {
                        case "sin": return Math.sin(inner);
                        case "cos": return Math.cos(inner);
                        case "tan": return Math.tan(inner);
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    private Expression exactSin(Fraction multiple) {
        Long steps = multiple.toScaledSteps(12);
        if (steps == null) {
            return null;
        }
        int normalized = (int) ((steps % 24 + 24) % 24);
        switch (normalized) {
            case 0:
            case 12:
                return buildNumberExpression(Number.integer(0));
            case 2:
            case 10:
                return buildFractionExpression(buildNumberExpression(Number.integer(1)),
                        buildNumberExpression(Number.integer(2)));
            case 3:
            case 9:
                return buildFractionExpression(sqrtExpression(2), buildNumberExpression(Number.integer(2)));
            case 4:
            case 8:
                return buildFractionExpression(sqrtExpression(3), buildNumberExpression(Number.integer(2)));
            case 6:
                return buildNumberExpression(Number.integer(1));
            case 14:
            case 22:
                return negateExpression(buildFractionExpression(buildNumberExpression(Number.integer(1)),
                        buildNumberExpression(Number.integer(2))));
            case 15:
            case 21:
                return negateExpression(buildFractionExpression(sqrtExpression(2),
                        buildNumberExpression(Number.integer(2))));
            case 16:
            case 20:
                return negateExpression(buildFractionExpression(sqrtExpression(3),
                        buildNumberExpression(Number.integer(2))));
            case 18:
                return buildNumberExpression(Number.integer(-1));
            default:
                return null;
        }
    }

    private Expression exactCos(Fraction multiple) {
        Long steps = multiple.toScaledSteps(12);
        if (steps == null) {
            return null;
        }
        int normalized = (int) ((steps % 24 + 24) % 24);
        switch (normalized) {
            case 0:
                return buildNumberExpression(Number.integer(1));
            case 12:
                return buildNumberExpression(Number.integer(-1));
            case 2:
            case 22:
                return buildFractionExpression(sqrtExpression(3), buildNumberExpression(Number.integer(2)));
            case 3:
            case 21:
                return buildFractionExpression(sqrtExpression(2), buildNumberExpression(Number.integer(2)));
            case 4:
            case 20:
                return buildFractionExpression(buildNumberExpression(Number.integer(1)),
                        buildNumberExpression(Number.integer(2)));
            case 6:
            case 18:
                return buildNumberExpression(Number.integer(0));
            case 8:
            case 16:
                return negateExpression(buildFractionExpression(buildNumberExpression(Number.integer(1)),
                        buildNumberExpression(Number.integer(2))));
            case 9:
            case 15:
                return negateExpression(buildFractionExpression(sqrtExpression(2),
                        buildNumberExpression(Number.integer(2))));
            case 10:
            case 14:
                return negateExpression(buildFractionExpression(sqrtExpression(3),
                        buildNumberExpression(Number.integer(2))));
            default:
                return null;
        }
    }

    private Expression exactTan(Fraction multiple) {
        Long steps = multiple.toScaledSteps(12);
        if (steps == null) {
            return null;
        }
        int normalized = (int) ((steps % 12 + 12) % 12);
        switch (normalized) {
            case 0:
                return buildNumberExpression(Number.integer(0));
            case 2:
                return buildFractionExpression(sqrtExpression(3), buildNumberExpression(Number.integer(3)));
            case 10:
                return negateExpression(buildFractionExpression(sqrtExpression(3),
                        buildNumberExpression(Number.integer(3))));
            case 3:
                return buildNumberExpression(Number.integer(1));
            case 9:
                return buildNumberExpression(Number.integer(-1));
            case 4:
                return sqrtExpression(3);
            case 8:
                return negateExpression(sqrtExpression(3));
            case 6:
                return buildNumberExpression(Number.constantInfinity());
            default:
                return null;
        }
    }

    private Expression sqrtExpression(int value) {
        return buildSqrtExpression(buildNumberExpression(Number.integer(value)));
    }

    private Fraction extractPiMultiple(Expression expr) {
        Expression target = unwrapParentheses(expr);
        if (target == null || target.getRoot() == null) {
            return null;
        }
        if (isPiNode(target)) {
            return Fraction.of(1, 1);
        }
        if (isTauNode(target)) {
            return Fraction.of(2, 1);
        }
        if (target.getRoot().type() == Types.NUMBER) {
            Number number = (Number) target.getRoot().value();
            if (Number.numericEquals(number, Number.integer(0))) {
                return Fraction.of(0, 1);
            }
            return null;
        }
        if (target.getRoot().type() == Types.OPERATOR) {
            char op = (char) target.getRoot().value();
            if (target.left == null && op == '-') {
                Fraction right = extractPiMultiple(target.right);
                return right != null ? right.negate() : null;
            }
            switch (op) {
                case '+': {
                    Fraction left = extractPiMultiple(target.left);
                    Fraction right = extractPiMultiple(target.right);
                    if (left != null && right != null) {
                        return left.add(right);
                    }
                    break;
                }
                case '-': {
                    Fraction left = extractPiMultiple(target.left);
                    Fraction right = extractPiMultiple(target.right);
                    if (left != null && right != null) {
                        return left.subtract(right);
                    }
                    break;
                }
                case '*': {
                    Fraction numericLeft = extractNumericFraction(target.left);
                    Fraction piRight = extractPiMultiple(target.right);
                    if (numericLeft != null && piRight != null) {
                        return numericLeft.multiply(piRight);
                    }
                    Fraction numericRight = extractNumericFraction(target.right);
                    Fraction piLeft = extractPiMultiple(target.left);
                    if (numericRight != null && piLeft != null) {
                        return piLeft.multiply(numericRight);
                    }
                    break;
                }
                case '/': {
                    Fraction numerator = extractPiMultiple(target.left);
                    Fraction denominator = extractNumericFraction(target.right);
                    if (numerator != null && denominator != null) {
                        return numerator.divide(denominator);
                    }
                    break;
                }
                default:
                    break;
            }
        }
        return null;
    }

    private Fraction extractNumericFraction(Expression expr) {
        Expression target = unwrapParentheses(expr);
        if (target == null || target.getRoot() == null) {
            return null;
        }
        if (target.getRoot().type() == Types.NUMBER) {
            Number number = (Number) target.getRoot().value();
            switch (number.type) {
                case INT:
                    return Fraction.of(number.intVal, 1);
                case BIGINT: {
                    try {
                        long value = number.bigVal.longValueExact();
                        return Fraction.of(value, 1);
                    } catch (ArithmeticException ex) {
                        return null;
                    }
                }
                case RATIONAL:
                    return Fraction.of(number.num, number.den);
                case BIGRATIONAL: {
                    try {
                        long num = number.bigNum.longValueExact();
                        long den = number.bigDen.longValueExact();
                        return Fraction.of(num, den);
                    } catch (ArithmeticException ex) {
                        return null;
                    }
                }
                default:
                    return null;
            }
        }
        if (target.getRoot().type() == Types.OPERATOR) {
            char op = (char) target.getRoot().value();
            if (target.left == null && op == '-') {
                Fraction right = extractNumericFraction(target.right);
                return right != null ? right.negate() : null;
            }
            Fraction left;
            Fraction right;
            switch (op) {
                case '+':
                    left = extractNumericFraction(target.left);
                    right = extractNumericFraction(target.right);
                    if (left != null && right != null) {
                        return left.add(right);
                    }
                    break;
                case '-':
                    left = extractNumericFraction(target.left);
                    right = extractNumericFraction(target.right);
                    if (left != null && right != null) {
                        return left.subtract(right);
                    }
                    break;
                case '*':
                    left = extractNumericFraction(target.left);
                    right = extractNumericFraction(target.right);
                    if (left != null && right != null) {
                        return left.multiply(right);
                    }
                    break;
                case '/':
                    left = extractNumericFraction(target.left);
                    right = extractNumericFraction(target.right);
                    if (left != null && right != null) {
                        return left.divide(right);
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    private Expression buildNumberExpression(Number number) {
        return new Expression(new Token(Types.NUMBER, number));
    }

    private Expression zero() {
        return buildNumberExpression(Number.integer(0));
    }

    private Expression one() {
        return buildNumberExpression(Number.integer(1));
    }

    private Expression buildSqrtExpression(Expression inner) {
        Expression sqrt = new Expression(new Token(Types.GROUPING, "sqrt"));
        sqrt.right = inner;
        return sqrt;
    }

    private Number extractExactNumber(Expression expr) {
        if (expr == null || expr.getRoot() == null) {
            return null;
        }
        Expression target = unwrapParentheses(expr);
        if (target == null || target.getRoot() == null) {
            return null;
        }
        Token root = target.getRoot();
        if (root.type() == Types.NUMBER) {
            Number number = (Number) root.value();
            return isExact(number) ? number : null;
        }
        if (root.type() == Types.OPERATOR && root.value() instanceof Character) {
            char op = (char) root.value();
            if (op == '-' && target.left == null) {
                Number inner = extractExactNumber(target.right);
                return inner == null ? null : negate(inner);
            }
        }
        return null;
    }

    private Expression buildProductExpression(Expression left, Expression right) {
        Expression product = new Expression(new Token(Types.OPERATOR, '*'));
        product.left = left;
        product.right = right;
        return product;
    }

    private Expression buildFractionExpression(Expression numerator, Expression denominator) {
        Expression fraction = new Expression(new Token(Types.OPERATOR, '/'));
        fraction.left = numerator;
        fraction.right = denominator;
        return fraction;
    }

    private Expression negateExpression(Expression expr) {
        return buildProductExpression(buildNumberExpression(Number.integer(-1)), expr);
    }

    private Expression cleanupDerivative(Expression expr) {
        if (expr == null) {
            return null;
        }
        if (expr.getRoot() == null) {
            return expr;
        }
        Expression cleanedLeft = cleanupDerivative(expr.left);
        Expression cleanedRight = cleanupDerivative(expr.right);
        expr.left = cleanedLeft;
        expr.right = cleanedRight;

        if (expr.getRoot().type() == Types.OPERATOR) {
            char op = (char) expr.getRoot().value();
            if (op == '*') {
                if (isUnitExpression(cleanedLeft)) {
                    return cloneExpression(cleanedRight);
                }
                if (isUnitExpression(cleanedRight)) {
                    return cloneExpression(cleanedLeft);
                }
            }
            if (op == '^' && cleanedRight != null && isUnitExpression(cleanedRight)) {
                return cloneExpression(cleanedLeft);
            }
        }
        return expr;
    }

    private boolean isUnitExpression(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.NUMBER
                && isOne((Number) expr.getRoot().value());
    }

    private Expression addExpr(Expression left, Expression right) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '+'));
        expr.left = left;
        expr.right = right;
        return expr;
    }

    private Expression subtractExpr(Expression left, Expression right) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '-'));
        expr.left = left;
        expr.right = right;
        return expr;
    }

    private Expression multiplyExpr(Expression left, Expression right) {
        return buildProductExpression(left, right);
    }

    private Expression divideExpr(Expression numerator, Expression denominator) {
        return buildFractionExpression(numerator, denominator);
    }

    private Expression powExpression(Expression base, Expression exponent) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '^'));
        expr.left = base;
        expr.right = exponent;
        return expr;
    }

    private Expression buildGrouping(String name, Expression inner) {
        Expression grouping = new Expression(new Token(Types.GROUPING, name));
        grouping.right = inner;
        return grouping;
    }

    private Expression buildSymbolExpression(String name) {
        return new Expression(new Token(Types.SYMBOL, name));
    }

    private Expression multiplyByImaginary(Expression magnitude) {
        Expression imaginary = buildSymbolExpression("i");
        if (magnitude == null || isUnitExpression(magnitude)) {
            return imaginary;
        }
        NumericFactor factor = extractNumericFactor(magnitude);
        Expression base = (factor.remainder != null)
                ? buildProductExpression(imaginary, factor.remainder)
                : imaginary;
        if (factor.coefficient == null || isOne(factor.coefficient)) {
            return base;
        }
        return buildProductExpression(buildNumberExpression(factor.coefficient), base);
    }

    private boolean isOne(Number number) {
        return Number.numericEquals(number, Number.integer(1));
    }

    private boolean isZero(Number number) {
        return Number.numericEquals(number, Number.integer(0));
    }

    private NumericFactor extractNumericFactor(Expression expr) {
        if (expr == null || expr.getRoot() == null) {
            return new NumericFactor(null, null);
        }
        if (isNumericExpression(expr)) {
            return new NumericFactor((Number) expr.getRoot().value(), null);
        }
        if (expr.getRoot().type() == Types.OPERATOR
                && expr.getRoot().value() instanceof Character
                && (Character) expr.getRoot().value() == '*') {
            NumericFactor left = extractNumericFactor(expr.left);
            NumericFactor right = extractNumericFactor(expr.right);
            Number coefficient = combineCoefficients(left.coefficient, right.coefficient);
            Expression remainder = combineRemainders(left.remainder, right.remainder);
            return new NumericFactor(coefficient, remainder);
        }
        return new NumericFactor(null, cloneExpression(expr));
    }

    private Number combineCoefficients(Number a, Number b) {
        if (a != null && b != null) {
            return Number.multiply(a, b);
        }
        return a != null ? a : b;
    }

    private Expression combineRemainders(Expression a, Expression b) {
        if (a != null && b != null) {
            return buildProductExpression(a, b);
        }
        return a != null ? a : b;
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

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a == 0 ? 1 : a;
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

    private static class NumericFactor {
        final Number coefficient;
        final Expression remainder;

        NumericFactor(Number coefficient, Expression remainder) {
            this.coefficient = coefficient;
            this.remainder = remainder;
        }
    }

    private static class Fraction {
        final long numerator;
        final long denominator;

        Fraction(long numerator, long denominator) {
            if (denominator == 0) {
                throw new ArithmeticException("Division by zero in fraction");
            }
            long sign = denominator < 0 ? -1 : 1;
            long n = numerator * sign;
            long d = Math.abs(denominator);
            long g = gcd(Math.abs(n), d);
            this.numerator = n / g;
            this.denominator = d / g;
        }

        static Fraction of(long num, long den) {
            return new Fraction(num, den);
        }

        Fraction negate() {
            return new Fraction(-numerator, denominator);
        }

        Fraction add(Fraction other) {
            long num = numerator * other.denominator + other.numerator * denominator;
            long den = denominator * other.denominator;
            return new Fraction(num, den);
        }

        Fraction subtract(Fraction other) {
            return add(other.negate());
        }

        Fraction multiply(Fraction other) {
            return new Fraction(numerator * other.numerator, denominator * other.denominator);
        }

        Fraction divide(Fraction other) {
            return new Fraction(numerator * other.denominator, denominator * other.numerator);
        }

        Fraction multiply(long value) {
            return new Fraction(numerator * value, denominator);
        }

        Fraction divide(long value) {
            return new Fraction(numerator, denominator * value);
        }

        Long toScaledSteps(int targetDenominator) {
            long scaled = numerator * targetDenominator;
            if (scaled % denominator != 0) {
                return null;
            }
            return scaled / denominator;
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

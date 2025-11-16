import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PolynomialFactorizer {
    private static final Number ZERO = Number.integer(0);
    private static final Number ONE = Number.integer(1);
    private static final Number NEG_ONE = Number.integer(-1);

    private final Evaluator evaluator = new Evaluator();

    public List<Expression> factor(Polynomial polynomial, String variable) {
        List<Expression> factors = new ArrayList<>();
        if (polynomial == null || polynomial.degree() < 0) {
            return factors;
        }

        Polynomial working = polynomial;

        Number rationalRoot;
        while (working.degree() > 0 && (rationalRoot = findRationalRoot(working)) != null) {
            Polynomial.DivisionResult division = working.divideByLinearFactor(rationalRoot);
            if (!Number.numericEquals(division.remainder, ZERO)) {
                break;
            }
            factors.add(simplify(linearFactor(variable, rationalRoot)));
            working = division.quotient;
        }

        int degree = working.degree();
        if (degree == 0) {
            Number constant = working.coefficient(0);
            if (!Number.numericEquals(constant, ONE) || factors.isEmpty()) {
                factors.add(simplify(numberExpr(constant)));
            }
            return factors;
        }

        if (degree == 1) {
            factors.add(simplify(linearFromCoefficients(working, variable)));
        } else if (degree == 2) {
            factors.add(simplify(polynomialExpression(working, variable)));
        } else {
            factors.add(simplify(polynomialExpression(working, variable)));
        }
        return factors;
    }

    private Expression linearFactor(String variable, Number root) {
        Expression symbol = symbolExpr(variable);
        Expression rootExpr = numberExpr(root);
        return subtract(symbol, rootExpr);
    }

    private Expression linearFromCoefficients(Polynomial polynomial, String variable) {
        Number slope = polynomial.coefficient(1);
        Number constant = polynomial.coefficient(0);
        Expression symbol = symbolExpr(variable);
        Expression term = multiply(numberExpr(slope), symbol);
        if (Number.numericEquals(constant, ZERO)) {
            return term;
        }
        return add(term, numberExpr(constant));
    }

    private Expression polynomialExpression(Polynomial polynomial, String variable) {
        Expression sum = null;
        for (int degree = polynomial.degree(); degree >= 0; degree--) {
            Number coeff = polynomial.coefficient(degree);
            if (Number.numericEquals(coeff, ZERO)) {
                continue;
            }
            Expression term = buildTerm(coeff, degree, variable);
            sum = sum == null ? term : add(sum, term);
        }
        if (sum == null) {
            sum = numberExpr(ZERO);
        }
        return sum;
    }

    private Expression buildTerm(Number coefficient, int degree, String variable) {
        if (degree == 0) {
            return numberExpr(coefficient);
        }
        Expression symbol = symbolExpr(variable);
        Expression power;
        if (degree == 1) {
            power = symbol;
        } else {
            power = pow(symbol, degree);
        }
        if (Number.numericEquals(coefficient, ONE)) {
            return cloneExpression(power);
        }
        if (Number.numericEquals(coefficient, NEG_ONE)) {
            return negate(power);
        }
        return multiply(numberExpr(coefficient), power);
    }

    private Expression pow(Expression base, int exponent) {
        if (exponent <= 1) {
            return cloneExpression(base);
        }
        Expression expr = new Expression(new Token(Types.OPERATOR, '^'));
        expr.left = cloneExpression(base);
        expr.right = numberExpr(Number.integer(exponent));
        return expr;
    }

    private Number findRationalRoot(Polynomial polynomial) {
        IntegerPolynomial integerized = integerize(polynomial);
        if (integerized == null) {
            return null;
        }
        BigInteger leading = integerized.coefficients.get(integerized.degree);
        BigInteger constant = integerized.coefficients.get(0);
        if (constant.equals(BigInteger.ZERO)) {
            return ZERO;
        }
        Set<Long> numerators = divisors(constant);
        Set<Long> denominators = divisors(leading);
        if (denominators.isEmpty()) {
            denominators.add(1L);
        }
        for (long p : numerators) {
            for (long q : denominators) {
                if (q == 0) continue;
                Number candidate = Number.rational(p, q);
                if (Number.numericEquals(polynomial.evaluate(candidate), ZERO)) {
                    return candidate;
                }
                Number negCandidate = Number.rational(-p, q);
                if (Number.numericEquals(polynomial.evaluate(negCandidate), ZERO)) {
                    return negCandidate;
                }
            }
        }
        return null;
    }

    private IntegerPolynomial integerize(Polynomial polynomial) {
        BigInteger lcm = BigInteger.ONE;
        ArrayList<BigInteger> numerators = new ArrayList<>();
        ArrayList<BigInteger> denominators = new ArrayList<>();
        for (Number coeff : polynomial.getCoefficients()) {
            FractionParts parts = toFraction(coeff);
            if (parts == null) {
                return null;
            }
            numerators.add(parts.numerator);
            denominators.add(parts.denominator);
            BigInteger absDen = parts.denominator.abs();
            lcm = lcm(lcm, absDen);
        }
        ArrayList<BigInteger> integerCoeffs = new ArrayList<>();
        for (int i = 0; i < numerators.size(); i++) {
            BigInteger scale = lcm.divide(denominators.get(i));
            integerCoeffs.add(numerators.get(i).multiply(scale));
        }
        return new IntegerPolynomial(integerCoeffs);
    }

    private FractionParts toFraction(Number number) {
        return switch (number.type) {
            case INT -> new FractionParts(BigInteger.valueOf(number.intVal), BigInteger.ONE);
            case BIGINT -> new FractionParts(number.bigVal, BigInteger.ONE);
            case RATIONAL -> new FractionParts(BigInteger.valueOf(number.num), BigInteger.valueOf(number.den));
            case BIGRATIONAL -> new FractionParts(number.bigNum, number.bigDen);
            default -> null;
        };
    }

    private BigInteger lcm(BigInteger a, BigInteger b) {
        if (a.equals(BigInteger.ZERO) || b.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }
        return a.divide(a.gcd(b)).multiply(b).abs();
    }

    private Set<Long> divisors(BigInteger value) {
        BigInteger absValue = value.abs();
        try {
            long numeric = absValue.longValueExact();
            Set<Long> result = new LinkedHashSet<>();
            for (long i = 1; i * i <= numeric; i++) {
                if (numeric % i == 0) {
                    result.add(i);
                    result.add(numeric / i);
                }
            }
            return result;
        } catch (ArithmeticException ex) {
            return new LinkedHashSet<>();
        }
    }

    private Expression simplify(Expression expr) {
        return evaluator.simplify(expr);
    }

    private Expression numberExpr(Number number) {
        return new Expression(new Token(Types.NUMBER, number));
    }

    private Expression symbolExpr(String name) {
        return new Expression(new Token(Types.SYMBOL, name));
    }

    private Expression add(Expression left, Expression right) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '+'));
        expr.left = left;
        expr.right = right;
        return expr;
    }

    private Expression subtract(Expression left, Expression right) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '-'));
        expr.left = left;
        expr.right = right;
        return expr;
    }

    private Expression multiply(Expression left, Expression right) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '*'));
        expr.left = left;
        expr.right = right;
        return expr;
    }

    private Expression negate(Expression expr) {
        Expression node = new Expression(new Token(Types.OPERATOR, '-'));
        node.right = expr;
        return node;
    }

    private Expression cloneExpression(Expression expr) {
        if (expr == null) {
            return null;
        }
        Expression clone = new Expression(expr.getRoot());
        clone.left = cloneExpression(expr.getLeft());
        clone.right = cloneExpression(expr.getRight());
        return clone;
    }

    private static class FractionParts {
        final BigInteger numerator;
        final BigInteger denominator;

        FractionParts(BigInteger numerator, BigInteger denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }

    private static class IntegerPolynomial {
        final List<BigInteger> coefficients;
        final int degree;

        IntegerPolynomial(List<BigInteger> coefficients) {
            this.coefficients = coefficients;
            int deg = coefficients.size() - 1;
            while (deg >= 0 && coefficients.get(deg).equals(BigInteger.ZERO)) {
                deg--;
            }
            this.degree = deg;
        }
    }
}

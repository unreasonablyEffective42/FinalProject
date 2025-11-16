import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;

public class PolynomialSolver {
    private static final Number ZERO = Number.integer(0);
    private static final Number ONE = Number.integer(1);
    private static final Number TWO = Number.integer(2);
    private static final Number FOUR = Number.integer(4);

    private final Evaluator evaluator = new Evaluator();

    public List<Expression> solve(Polynomial polynomial) {
        if (polynomial == null || polynomial.isZero()) {
            return Collections.emptyList();
        }

        List<Expression> roots = new ArrayList<>();
        Polynomial working = polynomial;

        Number rationalRoot;
        while ((rationalRoot = findRationalRoot(working)) != null) {
            Polynomial.DivisionResult division = working.divideByLinearFactor(rationalRoot);
            if (!Number.numericEquals(division.remainder, ZERO)) {
                break;
            }
            roots.add(simplify(numberExpr(rationalRoot)));
            working = division.quotient;
            if (working.degree() <= 0) {
                break;
            }
        }

        int degree = working.degree();
        if (degree < 1) {
            return roots;
        }

        if (degree == 1) {
            roots.add(simplify(solveLinear(working)));
            return roots;
        }
        if (degree == 2) {
            roots.addAll(solveQuadratic(working));
            return roots;
        }
        if (degree == 4 && isBiquadratic(working)) {
            roots.addAll(solveBiquadratic(working));
            return roots;
        }

        roots.addAll(approximateRoots(working));
        return roots;
    }

    private Expression solveLinear(Polynomial polynomial) {
        Number a = polynomial.coefficient(1);
        Number b = polynomial.coefficient(0);
        Expression numerator = negate(numberExpr(b));
        Expression denominator = numberExpr(a);
        return divide(numerator, denominator);
    }

    private List<Expression> solveQuadratic(Polynomial polynomial) {
        Number a = polynomial.coefficient(2);
        Number b = polynomial.coefficient(1);
        Number c = polynomial.coefficient(0);

        Expression negB = negate(numberExpr(b));
        Expression bSquared = multiply(numberExpr(b), numberExpr(b));
        Expression fourAC = multiply(numberExpr(FOUR),
                multiply(numberExpr(a), numberExpr(c)));
        Expression discriminant = subtract(bSquared, fourAC);
        Expression sqrtDiscriminant = sqrt(discriminant);
        Expression denominator = multiply(numberExpr(TWO), numberExpr(a));

        Expression positive = divide(add(negB, sqrtDiscriminant), denominator);
        Expression negative = divide(subtract(negB, sqrtDiscriminant), denominator);

        List<Expression> solutions = new ArrayList<>();
        solutions.add(simplify(positive));
        solutions.add(simplify(negative));
        return solutions;
    }

    private List<Expression> solveBiquadratic(Polynomial polynomial) {
        Number a = polynomial.coefficient(4);
        Number b = polynomial.coefficient(2);
        Number c = polynomial.coefficient(0);
        Polynomial substituted = new Polynomial(new ArrayList<>(Arrays.asList(c, b, a)));
        List<Expression> yRoots = solveQuadratic(substituted);
        List<Expression> xRoots = new ArrayList<>();
        for (Expression yRoot : yRoots) {
            Expression sqrtExpr = sqrt(cloneExpression(yRoot));
            xRoots.add(simplify(cloneExpression(sqrtExpr)));
            xRoots.add(simplify(negate(sqrtExpr)));
        }
        return xRoots;
    }

    private boolean isBiquadratic(Polynomial polynomial) {
        if (polynomial.degree() != 4) {
            return false;
        }
        return isZero(polynomial.coefficient(1)) && isZero(polynomial.coefficient(3));
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
                Number evaluation = polynomial.evaluate(candidate);
                if (Number.numericEquals(evaluation, ZERO)) {
                    return candidate;
                }
                Number negCandidate = Number.rational(-p, q);
                evaluation = polynomial.evaluate(negCandidate);
                if (Number.numericEquals(evaluation, ZERO)) {
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
            Fraction frac = toFraction(coeff);
            if (frac == null) {
                return null;
            }
            numerators.add(frac.numerator);
            denominators.add(frac.denominator);
            BigInteger absDen = frac.denominator.abs();
            lcm = lcm(lcm, absDen);
        }
        ArrayList<BigInteger> integerCoeffs = new ArrayList<>();
        for (int i = 0; i < numerators.size(); i++) {
            BigInteger scale = lcm.divide(denominators.get(i));
            integerCoeffs.add(numerators.get(i).multiply(scale));
        }
        return new IntegerPolynomial(integerCoeffs);
    }

    private Fraction toFraction(Number number) {
        return switch (number.type) {
            case INT -> new Fraction(BigInteger.valueOf(number.intVal), BigInteger.ONE);
            case BIGINT -> new Fraction(number.bigVal, BigInteger.ONE);
            case RATIONAL -> new Fraction(BigInteger.valueOf(number.num), BigInteger.valueOf(number.den));
            case BIGRATIONAL -> new Fraction(number.bigNum, number.bigDen);
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
            return new HashSet<>();
        }
    }

    private List<Expression> approximateRoots(Polynomial polynomial) {
        List<Double> estimates = new ArrayList<>();
        double lower = -10.0;
        double upper = 10.0;
        int samples = 400;
        double step = (upper - lower) / samples;
        double prevX = lower;
        double prevVal = polynomial.evaluate(prevX);
        for (int i = 1; i <= samples; i++) {
            double x = lower + i * step;
            double val = polynomial.evaluate(x);
            if (Double.isNaN(val) || Double.isNaN(prevVal)) {
                prevX = x;
                prevVal = val;
                continue;
            }
            if (Math.abs(val) < 1e-7) {
                estimates.add(x);
            }
            if (prevVal * val < 0) {
                estimates.add(bisect(polynomial, prevX, x));
            }
            prevX = x;
            prevVal = val;
        }
        estimates = deduplicate(estimates);
        List<Expression> expressions = new ArrayList<>();
        for (double value : estimates) {
            expressions.add(numberExpr(Number.real(value)));
        }
        return expressions;
    }

    private double bisect(Polynomial polynomial, double left, double right) {
        double a = left;
        double b = right;
        double mid = (a + b) / 2.0;
        for (int i = 0; i < 60; i++) {
            double fMid = polynomial.evaluate(mid);
            double fA = polynomial.evaluate(a);
            if (Math.abs(fMid) < 1e-9) {
                break;
            }
            if (fA * fMid < 0) {
                b = mid;
            } else {
                a = mid;
            }
            mid = (a + b) / 2.0;
        }
        return mid;
    }

    private List<Double> deduplicate(List<Double> values) {
        Collections.sort(values);
        ArrayList<Double> unique = new ArrayList<>();
        double last = Double.NaN;
        for (double value : values) {
            if (unique.isEmpty() || Math.abs(value - last) > 1e-6) {
                unique.add(value);
                last = value;
            }
        }
        return unique;
    }

    private boolean isZero(Number number) {
        return Number.numericEquals(number, ZERO);
    }

    private Expression numberExpr(Number number) {
        return new Expression(new Token(Types.NUMBER, number));
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

    private Expression divide(Expression numerator, Expression denominator) {
        Expression expr = new Expression(new Token(Types.OPERATOR, '/'));
        expr.left = numerator;
        expr.right = denominator;
        return expr;
    }

    private Expression negate(Expression expr) {
        Expression node = new Expression(new Token(Types.OPERATOR, '-'));
        node.right = expr;
        return node;
    }

    private Expression sqrt(Expression argument) {
        Expression expr = new Expression(new Token(Types.GROUPING, "sqrt"));
        expr.right = argument;
        return expr;
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

    private Expression simplify(Expression expr) {
        Expression simplified = evaluator.simplify(expr);
        Expression normalized = evaluator.normalizeProduct(simplified);
        if (normalized != simplified) {
            return evaluator.simplify(normalized);
        }
        return simplified;
    }

    private static class Fraction {
        final BigInteger numerator;
        final BigInteger denominator;

        Fraction(BigInteger numerator, BigInteger denominator) {
            if (denominator.signum() == 0) {
                throw new ArithmeticException("Division by zero");
            }
            BigInteger sign = denominator.signum() < 0 ? BigInteger.valueOf(-1) : BigInteger.ONE;
            BigInteger n = numerator.multiply(sign);
            BigInteger d = denominator.abs();
            BigInteger g = n.gcd(d);
            this.numerator = n.divide(g);
            this.denominator = d.divide(g);
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

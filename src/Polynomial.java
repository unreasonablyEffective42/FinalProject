import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight polynomial model where coefficients are stored in ascending
 * order (index = degree). The coefficients rely on {@link Number} so we can
 * preserve exact arithmetic as long as upstream callers supply exact values.
 */
public class Polynomial {
    private static final Number ZERO = Number.integer(0);

    private final ArrayList<Number> coefficients;

    public Polynomial(List<Number> coefficients) {
        this.coefficients = new ArrayList<>(coefficients);
        trimTrailingZeros();
    }

    public static Polynomial zero() {
        return new Polynomial(Collections.singletonList(ZERO));
    }

    public static Polynomial one() {
        return new Polynomial(Collections.singletonList(Number.integer(1)));
    }

    public boolean isZero() {
        return degree() < 0;
    }

    public int degree() {
        for (int i = coefficients.size() - 1; i >= 0; i--) {
            if (!isZero(coefficients.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public Number leadingCoefficient() {
        int deg = degree();
        if (deg < 0) {
            return ZERO;
        }
        return coefficients.get(deg);
    }

    public Number coefficient(int power) {
        if (power < 0 || power >= coefficients.size()) {
            return ZERO;
        }
        return coefficients.get(power);
    }

    public List<Number> getCoefficients() {
        return Collections.unmodifiableList(coefficients);
    }

    public Polynomial add(Polynomial other) {
        int max = Math.max(coefficients.size(), other.coefficients.size());
        ArrayList<Number> result = new ArrayList<>(Collections.nCopies(max, ZERO));
        for (int i = 0; i < max; i++) {
            Number a = i < coefficients.size() ? coefficients.get(i) : ZERO;
            Number b = i < other.coefficients.size() ? other.coefficients.get(i) : ZERO;
            result.set(i, Number.add(a, b));
        }
        return new Polynomial(result);
    }

    public Polynomial subtract(Polynomial other) {
        return add(other.scale(Number.integer(-1)));
    }

    public Polynomial scale(Number scalar) {
        ArrayList<Number> scaled = new ArrayList<>(coefficients.size());
        for (Number coeff : coefficients) {
            scaled.add(Number.multiply(coeff, scalar));
        }
        return new Polynomial(scaled);
    }

    public Polynomial multiply(Polynomial other) {
        if (isZero() || other.isZero()) {
            return Polynomial.zero();
        }
        int size = degree() + other.degree() + 1;
        ArrayList<Number> result = new ArrayList<>(Collections.nCopies(size, ZERO));
        for (int i = 0; i < coefficients.size(); i++) {
            for (int j = 0; j < other.coefficients.size(); j++) {
                Number existing = result.get(i + j);
                Number product = Number.multiply(coefficients.get(i), other.coefficients.get(j));
                result.set(i + j, Number.add(existing, product));
            }
        }
        return new Polynomial(result);
    }

    public Polynomial pow(int exponent) {
        if (exponent < 0) {
            throw new IllegalArgumentException("Negative exponents are not supported");
        }
        Polynomial result = Polynomial.one();
        Polynomial base = this;
        int power = exponent;
        while (power > 0) {
            if ((power & 1) == 1) {
                result = result.multiply(base);
            }
            if (power > 1) {
                base = base.multiply(base);
            }
            power >>= 1;
        }
        return result;
    }

    public Number evaluate(Number value) {
        Number acc = ZERO;
        for (int i = degree(); i >= 0; i--) {
            acc = Number.add(Number.multiply(acc, value), coefficient(i));
        }
        return acc;
    }

    public double evaluate(double value) {
        double acc = 0.0;
        for (int i = degree(); i >= 0; i--) {
            acc = acc * value + Number.toDouble(coefficient(i));
        }
        return acc;
    }

    public DivisionResult divideByLinearFactor(Number root) {
        int deg = degree();
        if (deg <= 0) {
            return new DivisionResult(Polynomial.zero(), coefficients.get(0));
        }
        ArrayList<Number> quotientDescending = new ArrayList<>();
        Number accumulator = coefficient(deg);
        quotientDescending.add(accumulator);

        for (int i = deg - 1; i >= 1; i--) {
            Number term = Number.add(coefficient(i), Number.multiply(root, accumulator));
            quotientDescending.add(term);
            accumulator = term;
        }
        Number remainder = Number.add(coefficient(0), Number.multiply(root, accumulator));
        Collections.reverse(quotientDescending);
        Polynomial quotient = new Polynomial(quotientDescending);
        return new DivisionResult(quotient, remainder);
    }

    private static boolean isZero(Number number) {
        return Number.numericEquals(number, ZERO);
    }

    private void trimTrailingZeros() {
        int last = coefficients.size() - 1;
        while (last > 0 && isZero(coefficients.get(last))) {
            coefficients.remove(last);
            last--;
        }
        if (coefficients.isEmpty()) {
            coefficients.add(ZERO);
        }
    }

    public static class DivisionResult {
        public final Polynomial quotient;
        public final Number remainder;

        public DivisionResult(Polynomial quotient, Number remainder) {
            this.quotient = quotient;
            this.remainder = remainder;
        }
    }
}

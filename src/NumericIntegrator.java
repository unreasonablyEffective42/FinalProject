import java.util.HashMap;
import java.util.Map;

public class NumericIntegrator {
    private static final int DEFAULT_INTERVALS = 1000; // even

    public double integrate(Expression expression, String variable, double lower, double upper) {
        int intervals = DEFAULT_INTERVALS;
        if (intervals % 2 == 1) {
            intervals++;
        }
        double h = (upper - lower) / intervals;
        double sum = evaluate(expression, variable, lower) + evaluate(expression, variable, upper);
        for (int i = 1; i < intervals; i++) {
            double x = lower + i * h;
            double weight = (i % 2 == 0) ? 2.0 : 4.0;
            sum += weight * evaluate(expression, variable, x);
        }
        return (h / 3.0) * sum;
    }

    public double evaluate(Expression expression) {
        return evaluate(expression, new HashMap<>());
    }

    public double evaluate(Expression expression, String variable, double value) {
        Map<String, Double> env = new HashMap<>();
        env.put(variable, value);
        return evaluate(expression, env);
    }

    private double evaluate(Expression expression, Map<String, Double> env) {
        if (expression == null || expression.getRoot() == null) {
            return 0.0;
        }
        Object value = expression.getRoot().value();
        switch ((Types) expression.getRoot().type()) {
            case NUMBER:
                return Number.toDouble((Number) value);
            case SYMBOL:
                return env.getOrDefault(value.toString(), 0.0);
            case OPERATOR:
                char op = (char) value;
                double left = evaluate(expression.getLeft(), env);
                double right = evaluate(expression.getRight(), env);
                switch (op) {
                    case '+':
                        return left + right;
                    case '-':
                        return left - right;
                    case '*':
                        return left * right;
                    case '/':
                        return left / right;
                    case '^':
                        return Math.pow(left, right);
                    default:
                        return 0.0;
                }
            case PARENTHESES:
                return evaluate(expression.getRight(), env);
            case GROUPING:
                String name = value.toString();
                double argument = evaluate(expression.getRight(), env);
                switch (name) {
                    case "sqrt":
                        return Math.sqrt(argument);
                    case "sin":
                        return Math.sin(argument);
                    case "cos":
                        return Math.cos(argument);
                    case "tan":
                        return Math.tan(argument);
                    case "ln":
                        return Math.log(argument);
                    default:
                        return 0.0;
                }
            default:
                return 0.0;
        }
    }
}

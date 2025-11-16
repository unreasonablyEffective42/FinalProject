import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class Expression {
    Token root;
    Expression left;
    Expression right;

    Expression(Token root) {
        this.root = root;
        this.left = null;
        this.right = null;
    }

    Expression(Token root, Expression left, Expression right) {
        this.root = root;
        this.left = left;
        this.right = right;
    }

    Token getRoot() {
        return root;
    }

    Expression getLeft() {
        return left;
    }

    Expression getRight() {
        return right;
    }

    static void printExpr(Expression expr){
        if (expr != null){
            printExpr(expr.left);
            System.out.println(expr.root);
            printExpr(expr.right);
        }
    }
}

public class Parser {
    private static final Token EOF_TOKEN = new Token(Types.EOF, null);
    private final HashMap<Character, Integer> bindingPower = new HashMap<>();
    private final HashMap<Character, Boolean> rightAssociative = new HashMap<>();
    private final Lexer lexer;
    private final ArrayList<Token> tokens;
    private int position;
    private final boolean evaluateDerivative;
    private final boolean evaluateIntegrals;

    Parser(String src) {
        this(src, true, true);
    }

    Parser(String src, boolean evaluateDerivative) {
        this(src, evaluateDerivative, true);
    }

    Parser(String src, boolean evaluateDerivative, boolean evaluateIntegrals) {
        this.evaluateDerivative = evaluateDerivative;
        this.evaluateIntegrals = evaluateIntegrals;
        lexer = new Lexer(src);
        lexer.lexAll();
        tokens = new ArrayList<>(lexer.tokens);
        insertImplicitMultiplication(tokens);
        position = 0;
        initOperators();
    }

    private Parser(ArrayList<Token> preLexedTokens, boolean evaluateDerivative) {
        this(preLexedTokens, evaluateDerivative, true);
    }

    private Parser(ArrayList<Token> preLexedTokens, boolean evaluateDerivative, boolean evaluateIntegrals) {
        this.evaluateDerivative = evaluateDerivative;
        this.evaluateIntegrals = evaluateIntegrals;
        lexer = null;
        tokens = new ArrayList<>(preLexedTokens);
        insertImplicitMultiplication(tokens);
        position = 0;
        initOperators();
    }

    private void initOperators() {
        this.registerOperator('+', 10, false);
        this.registerOperator('-', 10, false);
        this.registerOperator('*', 20, false);
        this.registerOperator('/', 20, false);
        this.registerOperator('%', 20, false);
        this.registerOperator('^', 30, true);
    }

    private void registerOperator(char symbol, int power, boolean rightAssoc) {
        bindingPower.put(symbol, power);
        rightAssociative.put(symbol, rightAssoc);
    }

    private void insertImplicitMultiplication(ArrayList<Token> source) {
        ArrayList<Token> transformed = new ArrayList<>();
        Token prev = null;

        for (Token current : source) {
            if (prev != null && needsImplicitMultiplication(prev, current)) {
                transformed.add(new Token(Types.OPERATOR, '*'));
            }
            transformed.add(current);
            prev = current;
        }

        source.clear();
        source.addAll(transformed);
    }

    private boolean needsImplicitMultiplication(Token left, Token right) {
        return isValueToken(left) && startsExpression(right);
    }

    private boolean isValueToken(Token token) {
        Types type = (Types) token.type();
        switch (type) {
            case NUMBER:
            case SYMBOL:
                return true;
            case PARENTHESES:
                return token.value() instanceof Character && (Character) token.value() == ')';
            default:
                return false;
        }
    }

    private boolean startsExpression(Token token) {
        Types type = (Types) token.type();
        switch (type) {
            case NUMBER:
            case SYMBOL:
            case GROUPING:
            case PREFIX:
                return true;
            case PARENTHESES:
                return token.value() instanceof Character && (Character) token.value() == '(';
            default:
                return false;
        }
    }

    private Token peek() {
        if (position >= tokens.size()) {
            return EOF_TOKEN;
        }
        return tokens.get(position);
    }

    private Token advance() {
        if (position >= tokens.size()) {
            return EOF_TOKEN;
        }
        return tokens.get(position++);
    }

    private ArrayList<Token> collectScopedTokens() {
        ArrayList<Token> innerTokens = new ArrayList<>();
        int depth = 1;

        while (depth > 0) {
            Token next = advance();
            Types nextType = (Types) next.type();
            if (nextType == Types.EOF) {
                throw new IllegalStateException("Unterminated grouping");
            }

            if (nextType == Types.PARENTHESES) {
                char paren = (char) next.value();
                if (paren == '(') {
                    depth++;
                    innerTokens.add(next);
                    continue;
                } else if (paren == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                    innerTokens.add(next);
                    continue;
                }
            }

            innerTokens.add(next);
        }

        return innerTokens;
    }

    private Expression parseParens() {
        ArrayList<Token> innerTokens = collectScopedTokens();
        Parser innerParser = new Parser(new ArrayList<>(innerTokens), evaluateDerivative, evaluateIntegrals);
        Expression innerExpr = innerParser.parse();
        Token groupToken = new Token(Types.PARENTHESES, 'p');
        return new Expression(groupToken, null, innerExpr);
    }

    private Expression parseGrouping(Token groupingToken) {
        expect(Types.PARENTHESES, '(');
        ArrayList<Token> innerTokens = collectScopedTokens();

        if ("int".equals(groupingToken.value())) {
            return parseIntegralGrouping(groupingToken, innerTokens);
        }
        if ("integrate".equals(groupingToken.value())) {
            return parseNumericIntegral(innerTokens);
        }
        if ("dd".equals(groupingToken.value())) {
            return parseDerivativeGrouping(innerTokens);
        }
        if ("roots".equals(groupingToken.value())) {
            return parseRootsGrouping(groupingToken, innerTokens);
        }
        if ("factor".equals(groupingToken.value())) {
            return parseFactorGrouping(groupingToken, innerTokens);
        }

        Parser innerParser = new Parser(new ArrayList<>(innerTokens), evaluateDerivative, evaluateIntegrals);
        Expression innerExpr = innerParser.parse();

        if (innerExpr != null) {
            Token innerRoot = innerExpr.getRoot();
            Expression innerRight = innerExpr.getRight();
            if (innerRoot != null && innerRoot.type() == Types.PARENTHESES && innerRight != null) {
                innerExpr = innerRight;
            }
        }

        return new Expression(groupingToken, null, innerExpr);
    }

    private Expression parseIntegralGrouping(Token groupingToken, ArrayList<Token> innerTokens) {
        ArrayList<ArrayList<Token>> arguments = splitArguments(innerTokens);
        if (arguments.size() != 2 && arguments.size() != 4) {
            throw new IllegalStateException("Integral requires 2 or 4 arguments");
        }

        Expression integrand = parseTokens(arguments.get(0));
        Expression variable = parseTokens(arguments.get(1));
        Expression lower = arguments.size() == 4 ? parseTokens(arguments.get(2)) : null;
        Expression upper = arguments.size() == 4 ? parseTokens(arguments.get(3)) : null;

        Expression paramsList = buildIntegralParamList(variable, lower, upper);
        return new Expression(groupingToken, paramsList, integrand);
    }

    private Expression parseDerivativeGrouping(ArrayList<Token> innerTokens) {
        ArrayList<ArrayList<Token>> arguments = splitArguments(innerTokens);
        if (arguments.size() < 2) {
            throw new IllegalStateException("dd requires at least expression and variable");
        }

        Expression expression = parseTokens(arguments.get(0));
        Expression variableExpr = parseTokens(arguments.get(1));

        if (variableExpr == null || variableExpr.getRoot() == null || variableExpr.getRoot().type() != Types.SYMBOL) {
            throw new IllegalStateException("Variable in dd must be a symbol");
        }

        if (!evaluateDerivative) {
            Expression node = new Expression(new Token(Types.GROUPING, "dd"));
            node.left = expression;
            node.right = variableExpr;
            return node;
        }

        String variableName = variableExpr.getRoot().value().toString();
        Evaluator evaluator = new Evaluator();
        return evaluator.differentiate(expression, variableName);
    }

    private Expression parseNumericIntegral(ArrayList<Token> innerTokens) {
        ArrayList<ArrayList<Token>> arguments = splitArguments(innerTokens);
        if (arguments.size() != 4) {
            throw new IllegalStateException("integrate requires four arguments (expr, variable, lower, upper)");
        }

        Expression integrand = parseTokens(arguments.get(0));
        Expression variableExpr = parseTokens(arguments.get(1));
        Expression lowerExpr = parseTokens(arguments.get(2));
        Expression upperExpr = parseTokens(arguments.get(3));

        if (variableExpr == null || variableExpr.getRoot() == null || variableExpr.getRoot().type() != Types.SYMBOL) {
            throw new IllegalStateException("Variable in integrate must be a symbol");
        }
        if (!evaluateIntegrals) {
            return buildIntegrateNode(integrand, variableExpr, lowerExpr, upperExpr);
        }
        String variableName = variableExpr.getRoot().value().toString();

        NumericIntegrator integrator = new NumericIntegrator();
        double lower = integrator.evaluate(lowerExpr);
        double upper = integrator.evaluate(upperExpr);
        double result = integrator.integrate(integrand, variableName, lower, upper);

        return new Expression(new Token(Types.NUMBER, Number.real((float) result)));
    }

    private Expression parseRootsGrouping(Token groupingToken, ArrayList<Token> innerTokens) {
        ArrayList<ArrayList<Token>> arguments = splitArguments(innerTokens);
        if (arguments.size() != 2) {
            throw new IllegalStateException("roots requires expression and variable arguments");
        }
        Expression polynomialExpr = parseTokens(arguments.get(0));
        Expression variableExpr = parseTokens(arguments.get(1));
        if (variableExpr == null || variableExpr.getRoot() == null || variableExpr.getRoot().type() != Types.SYMBOL) {
            throw new IllegalStateException("Variable in roots must be a symbol");
        }
        if (!evaluateIntegrals) {
            Expression node = new Expression(groupingToken);
            node.left = polynomialExpr;
            node.right = variableExpr;
            return node;
        }
        String variableName = variableExpr.getRoot().value().toString();
        PolynomialExtractor extractor = new PolynomialExtractor();
        Polynomial polynomial = extractor.extract(polynomialExpr, variableName);
        if (polynomial == null || polynomial.degree() < 1) {
            throw new IllegalStateException("Expression is not a polynomial in " + variableName);
        }
        PolynomialSolver solver = new PolynomialSolver();
        List<Expression> roots = solver.solve(polynomial);
        return buildRootsResultExpression(roots);
    }

    private Expression parseFactorGrouping(Token groupingToken, ArrayList<Token> innerTokens) {
        ArrayList<ArrayList<Token>> arguments = splitArguments(innerTokens);
        if (arguments.size() != 2) {
            throw new IllegalStateException("factor requires expression and variable arguments");
        }
        Expression polynomialExpr = parseTokens(arguments.get(0));
        Expression variableExpr = parseTokens(arguments.get(1));
        if (variableExpr == null || variableExpr.getRoot() == null || variableExpr.getRoot().type() != Types.SYMBOL) {
            throw new IllegalStateException("Variable in factor must be a symbol");
        }
        if (!evaluateIntegrals) {
            Expression node = new Expression(groupingToken);
            node.left = polynomialExpr;
            node.right = variableExpr;
            return node;
        }
        String variableName = variableExpr.getRoot().value().toString();
        PolynomialExtractor extractor = new PolynomialExtractor();
        Polynomial polynomial = extractor.extract(polynomialExpr, variableName);
        if (polynomial == null || polynomial.degree() < 1) {
            throw new IllegalStateException("Expression is not a polynomial in " + variableName);
        }
        PolynomialFactorizer factorizer = new PolynomialFactorizer();
        List<Expression> factors = factorizer.factor(polynomial, variableName);
        return buildFactorResultExpression(factors);
    }

    private ArrayList<ArrayList<Token>> splitArguments(ArrayList<Token> tokens) {
        ArrayList<ArrayList<Token>> result = new ArrayList<>();
        ArrayList<Token> accumulator = new ArrayList<>();
        int depth = 0;

        for (Token token : tokens) {
            if (token.type() == Types.PARENTHESES && (Character) token.value() == '(') {
                depth++;
            } else if (token.type() == Types.PARENTHESES && (Character) token.value() == ')') {
                depth--;
            }

            if (depth == 0 && token.type() == Types.OPERATOR && (Character) token.value() == ',') {
                result.add(new ArrayList<>(accumulator));
                accumulator.clear();
                continue;
            }

            accumulator.add(token);
        }

        if (!accumulator.isEmpty()) {
            result.add(new ArrayList<>(accumulator));
        }

        return result;
    }

    private Expression parseTokens(ArrayList<Token> tokens) {
        Parser parser = new Parser(new ArrayList<>(tokens), evaluateDerivative, evaluateIntegrals);
        return parser.parse();
    }

    private Expression buildIntegralParamList(Expression symbol, Expression lower, Expression upper) {
        Expression head = null;
        Expression tail = null;

        Expression[] entries = new Expression[]{symbol, lower, upper};
        for (Expression entry : entries) {
            if (entry == null) {
                continue;
            }
            Expression wrapper = new Expression(new Token(Types.GROUPING, "param"), entry, null);
            if (head == null) {
                head = wrapper;
                tail = wrapper;
            } else {
                tail.right = wrapper;
                tail = wrapper;
            }
        }
        return head;
    }

    private Expression buildIntegrateNode(Expression integrand, Expression variableExpr,
                                          Expression lowerExpr, Expression upperExpr) {
        Expression node = new Expression(new Token(Types.GROUPING, "integrate"));
        node.left = integrand;
        Expression params = new Expression(new Token(Types.GROUPING, "integrateParams"));
        params.left = variableExpr;
        params.right = new Expression(new Token(Types.GROUPING, "bounds"), lowerExpr, upperExpr);
        node.right = params;
        return node;
    }

    private Expression buildRootsResultExpression(List<Expression> roots) {
        Expression head = null;
        Expression tail = null;
        for (Expression rootExpr : roots) {
            Expression entry = new Expression(new Token(Types.GROUPING, "rootEntry"));
            entry.left = rootExpr;
            if (head == null) {
                head = entry;
            } else {
                tail.right = entry;
            }
            tail = entry;
        }
        Expression result = new Expression(new Token(Types.GROUPING, "rootsResult"));
        result.left = head;
        return result;
    }

    private Expression buildFactorResultExpression(List<Expression> factors) {
        Expression head = null;
        Expression tail = null;
        for (Expression factorExpr : factors) {
            Expression entry = new Expression(new Token(Types.GROUPING, "factorEntry"));
            entry.left = factorExpr;
            if (head == null) {
                head = entry;
            } else {
                tail.right = entry;
            }
            tail = entry;
        }
        Expression result = new Expression(new Token(Types.GROUPING, "factorResult"));
        result.left = head;
        return result;
    }

    private Expression createNumberExpression(Token token) {
        Object value = token.value();
        Number number;
        if (value instanceof Number existing) {
            number = existing;
        } else {
            number = parseNumberLiteral(value.toString());
        }
        Token numberToken = new Token(Types.NUMBER, number);
        return new Expression(numberToken);
    }

    private boolean isDivisionOperator(Token operator) {
        return operator.type() == Types.OPERATOR && operator.value() instanceof Character
                && (Character) operator.value() == '/';
    }

    private boolean isNumericExpression(Expression expr) {
        return expr != null
                && expr.getRoot() != null
                && expr.getRoot().type() == Types.NUMBER
                && expr.getRoot().value() instanceof Number;
    }

    private boolean canConvertToRational(Expression expr) {
        if (!isNumericExpression(expr)) {
            return false;
        }
        Number number = (Number) expr.getRoot().value();
        Number.Type type = number.type;
        return type == Number.Type.INT || type == Number.Type.BIGINT;
    }

    private Number parseNumberLiteral(String raw) {
        String val = raw.trim();
        if (val.isEmpty()) {
            throw new IllegalStateException("Empty numeric literal");
        }
        if (looksLikeDecimal(val)) {
            return parseDecimalLiteral(val);
        }
        return parseIntegerLiteral(val);
    }

    private boolean looksLikeDecimal(String val) {
        return val.contains(".") || val.contains("e") || val.contains("E");
    }

    private Number parseDecimalLiteral(String literal) {
        try {
            double value = Double.parseDouble(literal);
            if (!Double.isFinite(value)) {
                return Number.real(new BigDecimal(literal));
            }
            return Number.real(value);
        } catch (NumberFormatException e) {
            return Number.real(new BigDecimal(literal));
        }
    }

    private Number parseIntegerLiteral(String literal) {
        try {
            return Number.integer(Long.parseLong(literal));
        } catch (NumberFormatException e) {
            return Number.integer(new BigInteger(literal));
        }
    }

    public Expression parse() {
        return parseExpression(0);
    }

    private Expression parseExpression(int minBindingPower) {
        Token token = advance();
        Expression left = nud(token);

        while (minBindingPower < leftBindingPower(peek())) {
            Token operator = advance();
            left = led(left, operator);
        }

        return left;
    }

    private Expression nud(Token token) {
        Types tokenType = (Types) token.type();
        switch (tokenType) {
            case NUMBER:
                return createNumberExpression(token);
            case SYMBOL:
                return new Expression(token);
            case OPERATOR:
                char op = (char) token.value();
                if (op == '+' || op == '-') {
                    Expression right = parseExpression(40);
                    return new Expression(token, null, right);
                }
                throw new IllegalStateException("Unsupported prefix operator: " + op);
            case PARENTHESES:
                if ((Character) token.value() != '(') {
                    throw new IllegalStateException("Unmatched closing paren");
                }
                return parseParens();
            case GROUPING:
                return parseGrouping(token);
            case PREFIX:
                Expression right = parseExpression(40);
                return new Expression(token, null, right);
            default:
                throw new IllegalStateException("Unexpected token in nud: " + token);
        }
    }

    private Expression led(Expression left, Token operator) {
        Expression right = parseExpression(rightBindingPower(operator));
        if (isDivisionOperator(operator)
                && canConvertToRational(left)
                && canConvertToRational(right)) {
            Number numerator = (Number) left.getRoot().value();
            Number denominator = (Number) right.getRoot().value();
            Number result = Number.rational(numerator, denominator);
            return new Expression(new Token(Types.NUMBER, result));
        }
        return new Expression(operator, left, right);
    }

    private int leftBindingPower(Token token) {
        Types tokenType = (Types) token.type();
        if (tokenType != Types.OPERATOR) {
            return 0;
        }
        Character symbol = (Character) token.value();
        return bindingPower.getOrDefault(symbol, 0);
    }

    private int rightBindingPower(Token operator) {
        Character symbol = (Character) operator.value();
        int base = bindingPower.getOrDefault(symbol, 0);
        if (rightAssociative.getOrDefault(symbol, false)) {
            return base - 1;
        }
        return base + 1;
    }

    private Token expect(Types type, Character value) {
        Token token = advance();
        Types tokenType = (Types) token.type();
        if (tokenType != type) {
            throw new IllegalStateException("Expected token of type " + type + " but got " + tokenType);
        }
        if (value != null && !value.equals(token.value())) {
            throw new IllegalStateException("Expected token value '" + value + "' but got '" + token.value() + "'");
        }
        return token;
    }

    public static void main(String[] args) {
        Parser parser = new Parser("123 + (345*b) - 8^2");
        Expression expression = parser.parse();
        Expression.printExpr(expression);
    }
}

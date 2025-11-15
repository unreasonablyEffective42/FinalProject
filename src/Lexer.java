import java.util.Arrays;
import java.util.ArrayList;

public class Lexer {
    Token<Types,Object> EOF = new Token(Types.EOF, null);
    String src;
    char current;
    int pos;
    int len;

    String[] reserved = {"sqrt", "sin", "cos", "tan", "ln", "log"};
    ArrayList<String> groupings = new ArrayList<>(Arrays.asList(reserved));
    String operators = "+-*/%^";
    public ArrayList<Token> tokens = new ArrayList<Token>();    
   
    
    Lexer(String src_){
        this.src = src_;
        pos = 0;
        len = src.length() - 1;
        current = src.charAt(pos);
    }

    void advance() {
        if (pos < len){
            current = src.charAt(++pos);
        } else {
            current = '#';
        }
    }

    void skipWhiteSpace(){
        do {
            this.advance();
            char current = src.charAt(pos);
        }while (Character.isWhitespace(current));
    }

    Token parseNumber(){
        StringBuilder num = new StringBuilder();
        boolean hasDot = false;
        while (Character.isDigit(current) || (!hasDot && current == '.')) {
            if (current == '.') {
                hasDot = true;
            }
            num.append(current);
            this.advance();
        }
        return new Token(Types.NUMBER, num.toString());
    }

    private char peekChar() {
        if (pos + 1 > len) {
            return '#';
        }
        return src.charAt(pos + 1);
    }

    private void skipInlineWhitespace() {
        while (pos <= len && Character.isWhitespace(current)) {
            advance();
        }
    }

    private String parseIdentifierString() {
        if (!Character.isLetter(current)) {
            throw new RuntimeException("Expected identifier");
        }
        StringBuilder builder = new StringBuilder();
        while (pos <= len && Character.isLetter(current)) {
            builder.append(current);
            advance();
        }
        return builder.toString();
    }

    private Token parseDerivativeToken() {
        // current is first 'd'
        advance(); // consume first d, move to second
        if (current != 'd') {
            throw new RuntimeException("Invalid derivative operator");
        }
        advance(); // move past second d
        skipInlineWhitespace();
        if (current != '(') {
            throw new RuntimeException("Derivative operator must be followed by '('");
        }
        advance(); // move inside parentheses
        skipInlineWhitespace();
        String first = parseIdentifierString();
        skipInlineWhitespace();
        String value;
        if (current == ')') {
            advance();
            value = String.format("\\frac{d}{d%s}", first);
        } else if (current == ',') {
            advance();
            skipInlineWhitespace();
            String second = parseIdentifierString();
            skipInlineWhitespace();
            if (current != ')') {
                throw new RuntimeException("Derivative operator missing closing ')'");
            }
            advance();
            value = String.format("\\frac{d%s}{d%s}", first, second);
        } else {
            throw new RuntimeException("Invalid derivative parameters");
        }
        return new Token(Types.PREFIX, value);
    }

    private Token parseLimitToken() {
        // current is '(' at entry
        advance(); // consume '('
        ArrayList<String> args = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int depth = 0;

        while (true) {
            if (current == '#') {
                throw new RuntimeException("Unterminated limit expression");
            }
            if (current == '(') {
                depth++;
                builder.append(current);
                advance();
                continue;
            }
            if (current == ')') {
                if (depth == 0) {
                    args.add(builder.toString().trim());
                    builder.setLength(0);
                    advance(); // consume ')'
                    break;
                } else {
                    depth--;
                    builder.append(current);
                    advance();
                    continue;
                }
            }
            if (current == ',' && depth == 0) {
                args.add(builder.toString().trim());
                builder.setLength(0);
                advance();
                skipInlineWhitespace();
                continue;
            }
            builder.append(current);
            advance();
        }

        if (args.size() != 2) {
            throw new RuntimeException("Limit requires exactly two arguments");
        }

        return new Token(Types.PREFIX, new LimitInfo(args.get(0), args.get(1)));
    }

    public Token nextToken(){
        while(true){
            if (Character.isWhitespace(current)){
                this.skipWhiteSpace();
            }
            if (Character.isDigit(current)){
                return this.parseNumber();
            }
            else if (current == 'd' && peekChar() == 'd') {
                return parseDerivativeToken();
            }
            else if (operators.contains(String.valueOf(current))) {
                Token tok =new Token(Types.OPERATOR, current);
                this.advance();
                return tok;
            }
            else if (Character.isLetter(current)){
                return parseIdentifierOrGrouping();
            }

            else if (current == ',') {
                this.advance();
                return new Token(Types.OPERATOR, ',');
            }
            else if (current == '('){
                this.advance();
                Token tok = new Token(Types.PARENTHESES, '(');
                return tok;
            }
            else if (current == ')'){
                this.advance();
                Token tok = new Token(Types.PARENTHESES, ')');
                return tok;
            }
            else if (current == '#') {
                return EOF;
            }
            else {
                throw new IllegalStateException("Unexpected character: " + current);
            }
        }
    }

    private Token parseIdentifierOrGrouping() {
        StringBuilder buffer = new StringBuilder();
        int startPos = pos;

        while (Character.isLetter(current)) {
            buffer.append(current);
            advance();
        }

        String identifier = buffer.toString();

        if ("lim".equals(identifier)) {
            if (current != '(') {
                throw new RuntimeException("Prefix 'lim' must be followed by '('");
            }
            return parseLimitToken();
        }

        if ("int".equals(identifier)) {
            if (current != '(') {
                throw new RuntimeException("Grouping 'int' must be followed by '('");
            }
            return new Token(Types.GROUPING, "int");
        }

        if (isConstant(identifier)) {
            return new Token(Types.NUMBER, lookupConstant(identifier));
        }

        if (groupings.contains(identifier)) {
            if (current != '(') {
                throw new RuntimeException("Grouping '" + identifier + "' must be followed by '('");
            }
            return new Token(Types.GROUPING, identifier);
        }

        // Not a grouping: emit full symbol token
        return new Token(Types.SYMBOL, identifier);
    }

    private boolean isConstant(String identifier) {
        String lowered = identifier.toLowerCase();
        return lowered.equals("pi") || lowered.equals("e") || lowered.equals("tau") || lowered.equals("infinity");
    }

    private Number lookupConstant(String identifier) {
        switch (identifier.toLowerCase()) {
            case "pi":
                return Number.constantPi();
            case "tau":
                return Number.constantTau();
            case "e":
                return Number.constantE();
            case "infinity":
                return Number.constantInfinity();
            default:
                throw new IllegalArgumentException("Unknown constant: " + identifier);
        }
    }

    public void showToks(){
        for (Token elem : this.tokens){
            System.out.println(elem);
        }
    }

    public void lexAll(){
        Token thisTok = this.nextToken();
        while(thisTok.type() != Types.EOF){
            tokens.add(thisTok);
            thisTok = this.nextToken();
        }
    }

    public static void main(String[] args){
        Lexer lexer = new Lexer("(123 + 534) / 325 * 2235 / 3253");
        lexer.lexAll();
        lexer.showToks();
    }

}

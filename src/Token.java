enum Types{
    NUMBER,
    OPERATOR,
    VARIABLE,
    PARENTHESES,
    GROUPING,
    SYMBOL,
    PREFIX,
    EOF
}

//Generic token type
public record Token<Types, T>(Types type, T value) {
    public boolean equals(Token<Types, T> other) {
        return (this.type.equals(other.type) && this.value.equals(other.value));
    }
}

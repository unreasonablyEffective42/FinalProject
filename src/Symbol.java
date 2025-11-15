public class Symbol {
    public final String name;
    public Symbol(String name) { this.name = name; }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Symbol)) return false;
        return name.equals(((Symbol)o).name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}

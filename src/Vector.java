import java.util.Arrays;

/**
 * Minimal vector container used by {@link Number} for composite numeric
 * operations. It stores arbitrary objects and exposes the length so that
 * arithmetic helpers in {@code Number} can implement element-wise ops.
 */
public class Vector {
    public final Object[] elems;
    public final int size;

    public Vector(Object[] elems) {
        this.elems = elems;
        this.size = elems.length;
    }

    public Vector(Vector other) {
        this.elems = Arrays.copyOf(other.elems, other.size);
        this.size = other.size;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("<");
        for (int i = 0; i < size; i++) {
            builder.append(elems[i]);
            if (i < size - 1) {
                builder.append(" ");
            }
        }
        builder.append(">");
        return builder.toString();
    }
}

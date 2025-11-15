import java.util.ArrayList;
import java.util.Objects;


public class LinkedList<T> {
  Pair<T, Object> list;
  private boolean charListTag = false;

  public LinkedList() {
      this.list = null;
  }

  // Proper list constructor
  public LinkedList(T elem, LinkedList<T> tail) {
      this.list = new Pair<>(elem, tail);
  }

  // Improper list constructor
  public LinkedList(T elem, Object tail) {
      this.list = new Pair<>(elem, tail);
  }

  // Single element list
  public LinkedList(T elem) {
      this.list = new Pair<>(elem, null);
  }

  public T head() {
      if (this.list == null){
          return null;
      }
      return list.first;
  }

  @SuppressWarnings("unchecked")
  public Object tail() {
      if (this.list == null){
          return null;
      }
      return list.second;
  }

  public boolean isEmpty() {
      return list == null;
  }

  @SafeVarargs
  LinkedList(T... elems){
    if (elems.length == 0){
      this.list = null;
      return;
    }

    this.list = new Pair<>(elems[0], new LinkedList<>());
    LinkedList<T> current = (LinkedList<T>) this.list.second;

    for (int i = 1;i < elems.length;i++){
      current.list = new Pair<>(elems[i], new LinkedList<>());
      current = (LinkedList<T>) current.list.second;
    }
  }
  
  LinkedList(ArrayList<T> elems){
    if (elems.size() == 0){
      this.list = null;
      return;
    }

    this.list = new Pair<>(elems.get(0),new LinkedList<>());
    LinkedList<T> current = (LinkedList<T>)this.list.second;

    for (int i = 1;i < elems.size();i++){
      current.list = new Pair<>(elems.get(i), new LinkedList<>());
      current = (LinkedList<T>) current.list.second;
    }
  }

  public boolean allString(){ 
    if (this.size() == 0){return false;}
    LinkedList current = this;
    while (!(current == null)){
      if (current.head() instanceof String || current.head() == null){
        current = (LinkedList) current.tail();
      }
      else {return false;}
     
    }
    return true;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LinkedList<?> other)) return false;
    if (this.charListTag != other.charListTag) return false;
    return equalsHelper(this, other);
  }

  private static boolean equalsHelper(LinkedList<?> xs, LinkedList<?> ys) {
    if (xs == null && ys == null) {
      return true;
    }
    if (xs == null || ys == null) {
      return false;
    } 
    if (xs.charListTag != ys.charListTag) {
      return false;
    }
    if (!Objects.equals(xs.head(), ys.head())) {
      return false;
    }
    Object xtail = xs.tail();
    Object ytail = ys.tail();
    if (xtail instanceof LinkedList<?> && ytail instanceof LinkedList<?>) {
      return equalsHelper((LinkedList<?>) xtail, (LinkedList<?>) ytail);
    } else {
      return Objects.equals(xtail, ytail);
    }
  }

    public static String listToRawString(LinkedList<?> list) {
        StringBuilder sb = new StringBuilder();
        LinkedList<?> current = list;
        while (current != null && current.head() != null) {
            Object h = current.head();
            if (h instanceof String s) sb.append(s);
            else sb.append(String.valueOf(h));
            Object tail = current.tail();
            if (!(tail instanceof LinkedList<?>)) break;
            current = (LinkedList<?>) tail;
        }
        return sb.toString();
    }

    public static LinkedList<String> fromString(String text) {
        LinkedList<String> result = new LinkedList<>();
        if (text == null) {
            result.markCharListRecursive();
            return result;
        }
        if (text.isEmpty()) {
            result.markCharListRecursive();
            return result;
        }
        ArrayList<String> chars = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            chars.add(String.valueOf(text.charAt(i)));
        }
        LinkedList<String> list = new LinkedList<>(chars);
        list.markCharListRecursive();
        return list;
    }

    public static boolean isCharList(Object value) {
        return value instanceof LinkedList<?> list && list.isCharList();
    }

    public static LinkedList<String> concatCharLists(LinkedList<?> prefix, LinkedList<?> suffix) {
        String combined = listToRawString(prefix) + listToRawString(suffix);
        return fromString(combined);
    }

    public boolean isCharList() {
        if (this.charListTag) {
            return true;
        }
        if (this.size() == 0) {
            return false;
        }
        return this.allString() && this.allSingleCharStrings();
    }

    @SuppressWarnings("unchecked")
    private void markCharListRecursive() {
        this.charListTag = true;
        if (this.list != null && this.list.second instanceof LinkedList<?>) {
            ((LinkedList<Object>) this.list.second).markCharListRecursive();
        }
    }
    @Override
    public String toString() {
        if (list == null) {
            return charListTag ? "\"\"" : "()";
        }

        StringBuilder sb = new StringBuilder();

        boolean isCharList = this.isCharList();

        if (isCharList) {
            sb.append("\"");
            LinkedList<?> current = this;
            while (current != null && current.head() != null) {
                sb.append(current.head());
                current = (LinkedList<?>) current.tail();
            }
            sb.append("\"");
            return sb.toString();
        }

        // Otherwise print as normal list
        sb.append("(");
        Object current = this;
        boolean first = true;

        while (current instanceof LinkedList) {
            LinkedList<?> cell = (LinkedList<?>) current;
            if (cell.list == null) break;

            Object head = cell.head();
            if (!first) sb.append(" ");
            first = false;

            // Handle each element type properly
            if (head instanceof Symbol sym) {
                sb.append(sym.name);
            } else if (head instanceof String s) {
                sb.append("\"").append(s).append("\"");
            } else if (head instanceof LinkedList<?> sublist) {
                sb.append(sublist.toString());
            } else {
                sb.append(String.valueOf(head));
            }

            Object tail = cell.tail();
            if (tail == null) break;
            if (tail instanceof LinkedList) {
                current = tail;
            } else {
                sb.append(" . ").append(tail);
                break;
            }
        }

        sb.append(")");
        return sb.toString();
    }

    /** Helper to detect if every element is a single-character Java String. */
    private boolean allSingleCharStrings() {
        LinkedList<?> current = this;
        while (current != null && current.head() != null) {
            Object h = current.head();
            if (!(h instanceof String s) || s.length() != 1) {
                return false;
            }
            Object t = current.tail();
            if (!(t instanceof LinkedList<?>)) break;
            current = (LinkedList<?>) t;
        }
        return true;
    }
    public void setHead(T newHead){
        this.list.first = newHead;
    }

    public void setTail(LinkedList lst){
       this.list.second = lst;
    }

  
    public int size() {
        if (this.list == null) return 0; // empty list 
        int count = 0;
        LinkedList<?> current = this;
        while (current != null && current.list != null) {
            count++;
            Object tail = current.tail();
            if (tail instanceof LinkedList<?>) {
                current = (LinkedList<?>) tail;
            } else {
                // improper list tail (non-LinkedList value)
                break;
            }
        }
        return count;
    }
}

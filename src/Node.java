import java.util.ArrayList;
import java.util.Collections;

/*
This is a custom generic Node type, for creating tree like structures. Any value can be used for the value field,
and it supports an arbitrary number of children. The children are currently stored as an ArrayList, but will be updated
to a custom List type
 */
@SuppressWarnings("rawtypes")
public class Node<T> implements Comparable<Node<Token>> {
    T value;
    ArrayList<Node<Token>> children = new ArrayList<Node<Token>>();

    //constructor for Nodes with variable number of children
    public Node(T value, ArrayList<Node<Token>> children_) {
        this.value = value;
        this.children.addAll(children_);
    }
    //constructor for leaf node
    public Node(T value){
        this.value = value;
    }
    public T getValue() {
        return value;
    }
    public void setValue(T value) {
        this.value = value;
    }
    //returns all child nodes
    public ArrayList<Node<Token>> getChildren() {
        return children;
    }
    //add an existing node as a child of this node
    public void addChild(Node<Token> child) {
        children.add(child);
    }
    
   
    public void removeChild(Node<T> child) {
        children.remove(child);
    }
    //Add multiple nodes at once as child nodes
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final void addChildren(Node<Token>... children_) {
        Collections.addAll(children, children_);
    }

    //create a child node from a value
    public void createChild(Token value){
        Node<Token> child = new Node<Token>(value);
        children.add(child);
    }

    //for debugging, traverses the tree and prints all values
    public void printNodes(int ind){
        System.out.println("   ".repeat(ind)+value);
        for(Node<Token> child : children){
            child.printNodes(ind + 1);
        }
    }
    //If a node has no children, it is a leaf
    public boolean isLeaf(){
        return children.isEmpty();
    }

    @Override
    public int compareTo(Node<Token> tokenNode) {
        return 0;
    }
}

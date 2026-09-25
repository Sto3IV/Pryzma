package net.pryzma.util;

/**
 * Intrusive doubly-linked list: callers own the {@link Node}s, so insertion, removal and
 * membership tests are O(1) and allocation-free. A node belongs to at most one list at a time.
 */
public class LinkedList<T> {
    private Node<T> first;
    private Node<T> last;
    private int size;

    public void addFirst(Node<T> node) {
        checkNoParent(node);
        if (first == null) {
            last = node;
        } else {
            node.next = first;
            first.prev = node;
        }
        first = node;
        node.parent = this;
        size++;
    }

    public void addLast(Node<T> node) {
        checkNoParent(node);
        if (last == null) {
            first = node;
        } else {
            node.prev = last;
            last.next = node;
        }
        last = node;
        node.parent = this;
        size++;
    }

    /** Inserts {@code node} after {@code nodePrev}; a {@code null} {@code nodePrev} means the head. */
    public void addAfter(Node<T> nodePrev, Node<T> node) {
        if (nodePrev == null) {
            addFirst(node);
            return;
        }
        if (nodePrev == last) {
            addLast(node);
            return;
        }
        checkParent(nodePrev);
        checkNoParent(node);
        Node<T> nodeNext = nodePrev.next;
        nodePrev.next = node;
        node.prev = nodePrev;
        nodeNext.prev = node;
        node.next = nodeNext;
        node.parent = this;
        size++;
    }

    public Node<T> remove(Node<T> node) {
        checkParent(node);
        Node<T> prev = node.prev;
        Node<T> next = node.next;
        if (prev == null) {
            first = next;
        } else {
            prev.next = next;
        }
        if (next == null) {
            last = prev;
        } else {
            next.prev = prev;
        }
        node.prev = null;
        node.next = null;
        node.parent = null;
        size--;
        return node;
    }

    public void moveAfter(Node<T> nodePrev, Node<T> node) {
        remove(node);
        addAfter(nodePrev, node);
    }

    public boolean contains(Node<T> node) {
        return node.parent == this;
    }

    public Node<T> getFirst() {
        return first;
    }

    public Node<T> getLast() {
        return last;
    }

    public int getSize() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void checkParent(Node<T> node) {
        if (node.parent != this) {
            throw new IllegalArgumentException("Node " + node + " belongs to another list: " + node.parent);
        }
    }

    private void checkNoParent(Node<T> node) {
        if (node.parent != null) {
            throw new IllegalArgumentException("Node " + node + " is already in a list: " + node.parent);
        }
    }

    public static class Node<T> {
        private final T item;
        private Node<T> prev;
        private Node<T> next;
        private LinkedList<T> parent;

        public Node(T item) {
            this.item = item;
        }

        public T getItem() {
            return item;
        }

        public Node<T> getPrev() {
            return prev;
        }

        public Node<T> getNext() {
            return next;
        }

        public LinkedList<T> getParent() {
            return parent;
        }

        @Override
        public String toString() {
            return String.valueOf(item);
        }
    }
}

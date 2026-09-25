package net.pryzma.render;

import net.pryzma.util.LinkedList;

/**
 * A section's slice of a {@link VboRegion}, in vertices. {@code position < 0} means the range holds
 * no data. The node links the ranges of one region in ascending position order.
 */
public class VboRange {
    private int position = -1;
    private int size;
    private final LinkedList.Node<VboRange> node = new LinkedList.Node<>(this);

    public int getPosition() {
        return position;
    }

    public int getSize() {
        return size;
    }

    public int getPositionNext() {
        return position + size;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public LinkedList.Node<VboRange> getNode() {
        return node;
    }

    public VboRange getPrev() {
        LinkedList.Node<VboRange> prev = node.getPrev();
        return prev == null ? null : prev.getItem();
    }

    public VboRange getNext() {
        LinkedList.Node<VboRange> next = node.getNext();
        return next == null ? null : next.getItem();
    }

    @Override
    public String toString() {
        return position + "/" + size + "/" + getPositionNext();
    }
}

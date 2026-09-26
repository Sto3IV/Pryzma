package net.pryzma.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LinkedListTest {
    private static List<String> forward(LinkedList<String> list) {
        List<String> items = new ArrayList<>();
        LinkedList.Node<String> last = null;
        for (LinkedList.Node<String> node = list.getFirst(); node != null; node = node.getNext()) {
            assertSame(last, node.getPrev());
            items.add(node.getItem());
            last = node;
        }
        assertSame(last, list.getLast());
        assertEquals(items.size(), list.getSize());
        return items;
    }

    @Test
    void keepsOrderAndLinksAcrossInsertionsAndRemovals() {
        LinkedList<String> list = new LinkedList<>();
        LinkedList.Node<String> a = new LinkedList.Node<>("a");
        LinkedList.Node<String> b = new LinkedList.Node<>("b");
        LinkedList.Node<String> c = new LinkedList.Node<>("c");
        LinkedList.Node<String> d = new LinkedList.Node<>("d");
        assertTrue(list.isEmpty());
        list.addLast(b);
        list.addFirst(a);
        list.addLast(d);
        list.addAfter(b, c);
        assertEquals(List.of("a", "b", "c", "d"), forward(list));
        assertTrue(list.contains(c));

        assertSame(c, list.remove(c));
        assertFalse(list.contains(c));
        assertNull(c.getPrev());
        assertNull(c.getNext());
        assertEquals(List.of("a", "b", "d"), forward(list));
        list.remove(a);
        list.remove(d);
        assertEquals(List.of("b"), forward(list));
        list.remove(b);
        assertTrue(list.isEmpty());
        assertNull(list.getFirst());
        assertNull(list.getLast());
    }

    @Test
    void addAfterNullOrLastMeansHeadOrTail() {
        LinkedList<String> list = new LinkedList<>();
        LinkedList.Node<String> b = new LinkedList.Node<>("b");
        list.addAfter(null, b);
        list.addAfter(null, new LinkedList.Node<>("a"));
        list.addAfter(b, new LinkedList.Node<>("c"));
        assertEquals(List.of("a", "b", "c"), forward(list));
    }

    @Test
    void moveAfterRelinksWithinTheList() {
        LinkedList<String> list = new LinkedList<>();
        List<LinkedList.Node<String>> nodes = new ArrayList<>();
        for (String s : List.of("a", "b", "c", "d")) {
            LinkedList.Node<String> node = new LinkedList.Node<>(s);
            nodes.add(node);
            list.addLast(node);
        }
        list.moveAfter(nodes.get(3), nodes.get(0));
        assertEquals(List.of("b", "c", "d", "a"), forward(list));
        list.moveAfter(null, nodes.get(2));
        assertEquals(List.of("c", "b", "d", "a"), forward(list));
        list.moveAfter(nodes.get(1), nodes.get(0));
        assertEquals(List.of("c", "b", "a", "d"), forward(list));
    }

    @Test
    void aNodeBelongsToOneListAtATime() {
        LinkedList<String> first = new LinkedList<>();
        LinkedList<String> second = new LinkedList<>();
        LinkedList.Node<String> node = new LinkedList.Node<>("x");
        first.addLast(node);
        assertThrows(IllegalArgumentException.class, () -> first.addLast(node));
        assertThrows(IllegalArgumentException.class, () -> second.addFirst(node));
        assertThrows(IllegalArgumentException.class, () -> second.remove(node));
        assertFalse(second.contains(node));
        first.remove(node);
        second.addLast(node);
        assertTrue(second.contains(node));
        assertEquals(0, first.getSize());
    }
}

package de.flog99.mapgui.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class Nodes {

    private Nodes() {
    }

    /**
     * Depth-first walk including the root.
     *
     * <p>Iterative to keep server-tick layout and hit-testing off the call stack: the widget tree depth is
     * bounded only by what a screen builds, and recursion puts that on the thread's stack.
     */
    public static void walk(Node root, Consumer<Node> visitor) {
        if (root == null) return;

        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            visitor.accept(node);

            // Pushed in reverse so the pop order is the children's natural left-to-right order.
            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
    }

    public static <T extends Node> List<T> collect(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        walk(root, node -> { if (type.isInstance(node)) { found.add(type.cast(node)); } });
        return found;
    }

    /**
     * Innermost node of the given type at a point, for routing scroll events.
     *
     * <p>Iterative equivalent of the recursive depth-first search: children are tried back to front, and a
     * node matches only when nothing under it does. A stack of frames carries the work the call stack used
     * to - each frame is a node whose children are still to be searched, and a match is found on the way
     * back out, innermost first.
     */
    public static <T extends Node> T findAt(Node root, Class<T> type, int x, int y) {
        if (root == null || root.hidden() || !root.bounds().contains(x, y)) return null;

        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(root, 0));

        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            Node node = frame.node;
            List<Node> children = node.children();

            if (frame.index < children.size()) {
                Node child = children.get(children.size() - 1 - frame.index);
                frame.index++;
                if (!child.hidden() && child.bounds().contains(x, y)) {
                    stack.push(new Frame(child, 0));
                }
                continue;
            }

            // Every child has been ruled out, so this node itself is the innermost match if it qualifies.
            stack.pop();
            if (type.isInstance(node)) {
                return type.cast(node);
            }
        }

        return null;
    }

    /** One node whose children are still being searched. */
    private static final class Frame {
        final Node node;
        int index;

        Frame(Node node, int index) {
            this.node = node;
            this.index = index;
        }
    }
}

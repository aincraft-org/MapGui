package de.flog99.mapgui.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractContainer<S extends AbstractContainer<S>> extends AbstractNode<S> {

    protected final List<Node> childNodes = new ArrayList<>();

    /**
     * The visible children, cached so a layout pass (measure then arrange, per container, per frame)
     * does not rebuild a throwaway list each time.
     *
     * <p>Cached because {@code hidden} is only ever set while the tree is being built - nothing flips it
     * after layout starts, so this cannot go stale. Invalidated whenever children are added.
     */
    private List<Node> visible;

    public S children(Node... nodes) {
        for (Node node : nodes) {
            if (node != null) {
                childNodes.add(node);
            }
        }
        visible = null;
        return self();
    }

    public S children(Collection<? extends Node> nodes) {
        for (Node node : nodes) {
            if (node != null) {
                childNodes.add(node);
            }
        }
        visible = null;
        return self();
    }

    @Override
    public List<Node> children() {
        return childNodes;
    }

    protected List<Node> visibleChildren() {
        List<Node> cached = visible;
        if (cached == null) {
            cached = new ArrayList<>(childNodes.size());
            for (Node node : childNodes) {
                if (!node.hidden()) {
                    cached.add(node);
                }
            }
            visible = cached;
        }
        return cached;
    }

    @Override
    protected void paintContent(Painter painter) {
        for (Node node : childNodes) {
            node.paint(painter);
        }
    }
}

package de.flog99.mapgui.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.flog99.mapgui.ui.Ui.Button;
import static de.flog99.mapgui.ui.Ui.Column;
import static de.flog99.mapgui.ui.Ui.Text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A/B equivalence for {@link AbstractContainer#visibleChildren}: the cached list must hold exactly the
 * children a fresh filter would - same membership, same order - and must be invalidated when children
 * are added, so a later layout pass sees new children.
 */
class VisibleChildrenAbTest {

    private static List<Node> freshFilter(AbstractContainer<?> container) {
        List<Node> visible = new ArrayList<>();
        for (Node node : container.children()) {
            if (!node.hidden()) {
                visible.add(node);
            }
        }
        return visible;
    }

    @Test
    void cachedListMatchesFreshFilter() {
        Panel container = Column(
                Button("a"),
                Text("b").hidden(true),
                Button("c"),
                Text("d")
        );

        List<Node> cached = container.visibleChildren();
        assertEquals(freshFilter(container), cached);
    }

    @Test
    void cachedListIsStableAcrossCalls() {
        Panel container = Column(Button("a"), Text("b"));
        assertSame(container.visibleChildren(), container.visibleChildren(),
                "the same list is handed back, which is what stops the per-pass allocation");
    }

    @Test
    void addingChildrenInvalidatesTheCache() {
        Panel container = Column(Button("a"));
        container.visibleChildren(); // warm the cache

        container.children(Button("b"));
        List<Node> cached = container.visibleChildren();
        assertEquals(2, cached.size());
        assertEquals(freshFilter(container), cached);
    }

    @Test
    void addingHiddenChildStillShownAfterInvalidation() {
        Panel container = Column(Button("a"));
        container.visibleChildren();

        container.children(Text("secret").hidden(true));
        List<Node> cached = container.visibleChildren();
        assertEquals(1, cached.size());
        assertEquals(freshFilter(container), cached);
    }
}

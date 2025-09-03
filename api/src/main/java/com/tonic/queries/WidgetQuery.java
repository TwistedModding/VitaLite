package com.tonic.queries;

import com.tonic.Static;
import com.tonic.queries.abstractions.AbstractQuery;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

public class WidgetQuery extends AbstractQuery<Widget, WidgetQuery>
{
    public WidgetQuery() {
        super(Arrays.asList(((Client)Static.getClient()).getWidgetRoots()));
    }

    public WidgetQuery(List<Widget> cache) {
        super(new ArrayList<>(cache));
    }

    public WidgetQuery(Widget[] cache) {
        super(Arrays.asList(cache));
    }

    public WidgetQuery(HashSet<Widget> cache) {
        super(new ArrayList<>(cache));
    }

    public WidgetQuery withId(int... id)
    {
        return removeIf(w -> !ArrayUtils.contains(id, w.getId()));
    }

    public WidgetQuery withItemId(int... itemId) {
        return removeIf(w -> !ArrayUtils.contains(itemId, w.getItemId()));
    }

    public WidgetQuery withParentId(int... itemId) {
        return removeIf(w -> !ArrayUtils.contains(itemId, w.getParentId()));
    }

    public WidgetQuery withText(String text)
    {
        return removeIf(w -> w.getText() == null || !w.getText().equalsIgnoreCase(text));
    }

    public WidgetQuery withTextContains(String... texts)
    {
        return removeIf(w -> w.getText() == null || Arrays.stream(texts).noneMatch(t -> w.getText().toLowerCase().contains(t.toLowerCase())));
    }

    public WidgetQuery withActions(String... actions)
    {
        return removeIf(w -> w.getActions() == null || Arrays.stream(actions).noneMatch(a -> Arrays.stream(w.getActions()).anyMatch(wa -> wa != null && wa.equalsIgnoreCase(a))));
    }

    public WidgetQuery isVisible()
    {
        return removeIf(Widget::isHidden);
    }

    public WidgetQuery isHidden()
    {
        return keepIf(Widget::isHidden);
    }

    public WidgetQuery isSelfVisible()
    {
        return removeIf(Widget::isSelfHidden);
    }

    public WidgetQuery isSelfHidden()
    {
        return keepIf(Widget::isSelfHidden);
    }

    public WidgetQuery withType(int... types)
    {
        return removeIf(w -> !ArrayUtils.contains(types, w.getType()));
    }

    public WidgetQuery withChildren()
    {
        return removeIf(w -> w.getChildren() == null || w.getChildren().length == 0);
    }

    public WidgetQuery withNoChildren()
    {
        return keepIf(w -> w.getChildren() == null || w.getChildren().length == 0);
    }

    public WidgetQuery withModelId(int... modelIds)
    {
        return removeIf(w -> !ArrayUtils.contains(modelIds, w.getModelId()));
    }

    public WidgetQuery withQuantityGreaterThan(int quantity)
    {
        return removeIf(w -> w.getItemQuantity() <= quantity);
    }

    public WidgetQuery withQuantityLessThan(int quantity)
    {
        return removeIf(w -> w.getItemQuantity() >= quantity);
    }

    public WidgetQuery withQuantity(int quantity)
    {
        return removeIf(w -> w.getItemQuantity() != quantity);
    }

    public WidgetQuery withWidget(Widget... widgets)
    {
        return removeIf(w -> !ArrayUtils.contains(widgets, w));
    }
}

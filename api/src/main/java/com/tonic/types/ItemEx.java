package com.tonic.types;

import com.tonic.Static;
import lombok.*;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;

@Getter
@RequiredArgsConstructor
public class ItemEx {
    private final Item item;
    private final int slot;

    public int getId() {
        return item.getId();
    }

    public String getName() {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getItemDefinition(item.getId()).getName());
    }

    public int getQuantity() {
        return item.getQuantity();
    }
}

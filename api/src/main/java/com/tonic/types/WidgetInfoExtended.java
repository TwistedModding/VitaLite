package com.tonic.types;

import net.runelite.api.widgets.WidgetID;

import java.util.Arrays;

public enum WidgetInfoExtended {
    PRAYER_THICK_SKIN(WidgetID.PRAYER_GROUP_ID, Prayer.THICK_SKIN),
    PRAYER_BURST_OF_STRENGTH(WidgetID.PRAYER_GROUP_ID, Prayer.BURST_OF_STRENGTH),
    PRAYER_CLARITY_OF_THOUGHT(WidgetID.PRAYER_GROUP_ID, Prayer.CLARITY_OF_THOUGHT),
    PRAYER_SHARP_EYE(WidgetID.PRAYER_GROUP_ID, Prayer.SHARP_EYE),
    PRAYER_MYSTIC_WILL(WidgetID.PRAYER_GROUP_ID, Prayer.MYSTIC_WILL),
    PRAYER_ROCK_SKIN(WidgetID.PRAYER_GROUP_ID, Prayer.ROCK_SKIN),
    PRAYER_SUPERHUMAN_STRENGTH(WidgetID.PRAYER_GROUP_ID, Prayer.SUPERHUMAN_STRENGTH),
    PRAYER_IMPROVED_REFLEXES(WidgetID.PRAYER_GROUP_ID, Prayer.IMPROVED_REFLEXES),
    PRAYER_RAPID_RESTORE(WidgetID.PRAYER_GROUP_ID, Prayer.RAPID_RESTORE),
    PRAYER_RAPID_HEAL(WidgetID.PRAYER_GROUP_ID, Prayer.RAPID_HEAL),
    PRAYER_PROTECT_ITEM(WidgetID.PRAYER_GROUP_ID, Prayer.PROTECT_ITEM),
    PRAYER_HAWK_EYE(WidgetID.PRAYER_GROUP_ID, Prayer.HAWK_EYE),
    PRAYER_MYSTIC_LORE(WidgetID.PRAYER_GROUP_ID, Prayer.MYSTIC_LORE),
    PRAYER_STEEL_SKIN(WidgetID.PRAYER_GROUP_ID, Prayer.STEEL_SKIN),
    PRAYER_ULTIMATE_STRENGTH(WidgetID.PRAYER_GROUP_ID, Prayer.ULTIMATE_STRENGTH),
    PRAYER_INCREDIBLE_REFLEXES(WidgetID.PRAYER_GROUP_ID, Prayer.INCREDIBLE_REFLEXES),
    PRAYER_PROTECT_FROM_MAGIC(WidgetID.PRAYER_GROUP_ID, Prayer.PROTECT_FROM_MAGIC),
    PRAYER_PROTECT_FROM_MISSILES(WidgetID.PRAYER_GROUP_ID, Prayer.PROTECT_FROM_MISSILES),
    PRAYER_PROTECT_FROM_MELEE(WidgetID.PRAYER_GROUP_ID, Prayer.PROTECT_FROM_MELEE),
    PRAYER_EAGLE_EYE(WidgetID.PRAYER_GROUP_ID, Prayer.EAGLE_EYE),
    PRAYER_MYSTIC_MIGHT(WidgetID.PRAYER_GROUP_ID, Prayer.MYSTIC_MIGHT),
    PRAYER_RETRIBUTION(WidgetID.PRAYER_GROUP_ID, Prayer.RETRIBUTION),
    PRAYER_REDEMPTION(WidgetID.PRAYER_GROUP_ID, Prayer.REDEMPTION),
    PRAYER_SMITE(WidgetID.PRAYER_GROUP_ID, Prayer.SMITE),
    PRAYER_PRESERVE(WidgetID.PRAYER_GROUP_ID, Prayer.PRESERVE),
    PRAYER_CHIVALRY(WidgetID.PRAYER_GROUP_ID, Prayer.CHIVALRY),
    PRAYER_PIETY(WidgetID.PRAYER_GROUP_ID, Prayer.PIETY),
    PRAYER_RIGOUR(WidgetID.PRAYER_GROUP_ID, Prayer.RIGOUR),
    PRAYER_AUGURY(WidgetID.PRAYER_GROUP_ID, Prayer.AUGURY),


    GRAND_EXCHANGE_OFFER1(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER1),
    GRAND_EXCHANGE_OFFER2(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER2),
    GRAND_EXCHANGE_OFFER3(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER3),
    GRAND_EXCHANGE_OFFER4(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER4),
    GRAND_EXCHANGE_OFFER5(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER5),
    GRAND_EXCHANGE_OFFER6(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER6),
    GRAND_EXCHANGE_OFFER7(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER7),
    GRAND_EXCHANGE_OFFER8(WidgetID.GRAND_EXCHANGE_GROUP_ID, GrandExchange.OFFER8),
    ;

    private final int id;

    WidgetInfoExtended(int id)
    {
        this.id = id;
    }

    WidgetInfoExtended(int groupId, int childId)
    {
        this.id = (groupId << 16) | childId;
    }

    /**
     * Gets the ID of the group-child pairing.
     *
     * @return the ID
     */
    public int getId()
    {
        return id;
    }

    /**
     * Gets the group ID of the pair.
     *
     * @return the group ID
     */
    public int getGroupId()
    {
        return id >> 16;
    }

    /**
     * Gets the ID of the child in the group.
     *
     * @return the child ID
     */
    public int getChildId()
    {
        return id & 0xffff;
    }

    /**
     * Gets the packed widget ID.
     *
     * @return the packed ID
     */
    public int getPackedId()
    {
        return id;
    }

    /**
     * Utility method that converts an ID returned by {@link #getId()} back
     * to its group ID.
     *
     * @param id passed group-child ID
     * @return the group ID
     */
    public static int TO_GROUP(int id)
    {
        return id >>> 16;
    }

    /**
     * Utility method that converts an ID returned by {@link #getId()} back
     * to its child ID.
     *
     * @param id passed group-child ID
     * @return the child ID
     */
    public static int TO_CHILD(int id)
    {
        return id & 0xFFFF;
    }

    /**
     * Packs the group and child IDs into a single integer.
     *
     * @param groupId the group ID
     * @param childId the child ID
     * @return the packed ID
     */
    public static int PACK(int groupId, int childId)
    {
        return groupId << 16 | childId;
    }

    public static WidgetInfoExtended ofId(int packed)
    {
        int childId = packed & 0xFFFF;
        int groupId = packed >>> 16;
        return of(groupId, childId);
    }

    public static WidgetInfoExtended of(int groupId, int childId)
    {
        return Arrays.stream(WidgetInfoExtended.values()).filter(w -> w.getChildId() == childId && w.getGroupId() == groupId).findFirst().orElse(null);
    }

    static class Prayer
    {
        static final int THICK_SKIN = 9;
        static final int BURST_OF_STRENGTH = 10;
        static final int CLARITY_OF_THOUGHT = 11;
        static final int SHARP_EYE = 27;
        static final int MYSTIC_WILL = 30;
        static final int ROCK_SKIN = 12;
        static final int SUPERHUMAN_STRENGTH = 13;
        static final int IMPROVED_REFLEXES = 14;
        static final int RAPID_RESTORE = 15;
        static final int RAPID_HEAL = 16;
        static final int PROTECT_ITEM = 17;
        static final int HAWK_EYE = 28;
        static final int MYSTIC_LORE = 31;
        static final int STEEL_SKIN = 18;
        static final int ULTIMATE_STRENGTH = 19;
        static final int INCREDIBLE_REFLEXES = 20;
        static final int PROTECT_FROM_MAGIC = 21;
        static final int PROTECT_FROM_MISSILES = 22;
        static final int PROTECT_FROM_MELEE = 23;
        static final int EAGLE_EYE = 29;
        static final int MYSTIC_MIGHT = 32;
        static final int RETRIBUTION = 24;
        static final int REDEMPTION = 25;
        static final int SMITE = 26;
        static final int PRESERVE = 37;
        static final int CHIVALRY = 34;
        static final int PIETY = 35;
        static final int RIGOUR = 33;
        static final int AUGURY = 36;
    }

    static class GrandExchange
    {
        static final int WINDOW_CONTAINER = 0;
        static final int WINDOW_BORDERS = 2;
        static final int HISTORY_BUTTON = 3;
        static final int BACK_BUTTON = 4;
        static final int OFFER1 = 7;
        static final int OFFER2 = 8;
        static final int OFFER3 = 9;
        static final int OFFER4 = 10;
        static final int OFFER5 = 11;
        static final int OFFER6 = 12;
        static final int OFFER7 = 13;
        static final int OFFER8 = 14;
        static final int OFFER_CONTAINER = 25;
        static final int OFFER_DESCRIPTION = 26;
        static final int OFFER_PRICE = 27;
        static final int OFFER_CONFIRM_BUTTON = 29;
    }
}

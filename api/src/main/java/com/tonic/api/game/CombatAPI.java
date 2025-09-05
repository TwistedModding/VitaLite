package com.tonic.api.game;

import com.tonic.Static;
import com.tonic.api.widgets.WidgetAPI;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import java.util.Arrays;

public class CombatAPI
{
    private static final int SPEC_VARP = 301;
    private static final int SPEC_ENERGY_VARP = 300;
    private static final int VENOM_THRESHOLD = 1000000;
    private static final int ANTIFIRE = 3981;
    private static final int SUPER_ANTIFIRE = 6101;

    public static boolean isRetaliating()
    {
        return VarAPI.getVarp(VarPlayerID.OPTION_NODEF) == 0;
    }

    public static void toggleRetaliate(boolean bool)
    {
        if(!isRetaliating() && bool)
        {
            WidgetAPI.interact(1, WidgetInfo.COMBAT_AUTO_RETALIATE, -1, -1);
        }
        else if(isRetaliating() && !bool)
        {
            WidgetAPI.interact(1, WidgetInfo.COMBAT_AUTO_RETALIATE, -1, -1);
        }
    }

    public static boolean isPoisoned()
    {
        return VarAPI.getVarp(VarPlayerID.POISON) > 0;
    }

    public static boolean isVenomed()
    {
        return VarAPI.getVarp(VarPlayerID.POISON) >= VENOM_THRESHOLD;
    }

    public static boolean isSpecEnabled()
    {
        return VarAPI.getVarp(SPEC_VARP) == 1;
    }

    public static int getSpecEnergy()
    {
        return VarAPI.getVarp(SPEC_ENERGY_VARP) / 10;
    }

    public static boolean isAntifired()
    {
        return VarAPI.getVar(ANTIFIRE) > 0;
    }

    public static boolean isSuperAntifired()
    {
        return VarAPI.getVar(SUPER_ANTIFIRE) > 0;
    }

    public static void toggleSpec()
    {
        if (isSpecEnabled())
        {
            return;
        }

        WidgetAPI.interact(1, 38862885, -1, -1);
    }

    public static void setAttackStyle(AttackStyle attackStyle)
    {
        if (attackStyle.widgetInfo == null)
        {
            return;
        }

        Client client = Static.getClient();
        Widget widget = Static.invoke(() -> client.getWidget(attackStyle.widgetInfo));
        if (widget != null)
        {
            WidgetAPI.interact(1, attackStyle.widgetInfo, -1, -1);
        }
    }

    public static AttackStyle getAttackStyle()
    {
        return AttackStyle.fromIndex(VarAPI.getVarp(43));
    }

    @Getter
    public enum AttackStyle
    {
        FIRST(0, WidgetInfo.COMBAT_STYLE_ONE),
        SECOND(1, WidgetInfo.COMBAT_STYLE_TWO),
        THIRD(2, WidgetInfo.COMBAT_STYLE_THREE),
        FOURTH(3, WidgetInfo.COMBAT_STYLE_FOUR),
        SPELLS(4, WidgetInfo.COMBAT_SPELL_BOX),
        SPELLS_DEFENSIVE(4, WidgetInfo.COMBAT_DEFENSIVE_SPELL_BOX),
        UNKNOWN(-1, null);

        private final int index;
        private final WidgetInfo widgetInfo;

        AttackStyle(int index, WidgetInfo widgetInfo)
        {
            this.index = index;
            this.widgetInfo = widgetInfo;
        }

        public static AttackStyle fromIndex(int index)
        {
            return Arrays.stream(values()).filter(x -> x.index == index)
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }
}

package com.tonic.api.widgets;

import com.tonic.api.game.GameAPI;
import com.tonic.api.game.VarAPI;
import com.tonic.data.magic.Spell;
import com.tonic.data.magic.SpellBook;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class MagicAPI
{
    private static final int AUTOCAST_VARP = 108;

    public static boolean isAutoCasting()
    {
        return VarAPI.getVarp(AUTOCAST_VARP) != 0;
    }

    public static Instant getLastHomeTeleportUsage()
    {
        return Instant.ofEpochSecond(VarAPI.getVarp(VarPlayerID.AIDE_TELE_TIMER) * 60L);
    }

    public static boolean isHomeTeleportOnCooldown()
    {
        return getLastHomeTeleportUsage().plus(30, ChronoUnit.MINUTES).isAfter(Instant.now());
    }

    public static void setBestAutoCast()
    {
        Spell bestSpell = comparator();
        bestSpell.setAutoCast();
    }

    public static void cast(Spell spell)
    {
        if(spell == null || !spell.canCast())
            return;

        spell.cast();
    }

    public static Spell comparator() {
        int level = GameAPI.getRealSkillLevel(Skill.MAGIC);
        Spell spell = null;
        for(Spell s : SpellBook.getCurrentOffensiveSpells()) {
            if(s.getLevel() > level)
                continue;

            if(!s.canCast())
                continue;

            if(spell != null && s.getLevel() < spell.getLevel())
                continue;

            spell = s;
        }

        return spell;
    }
}

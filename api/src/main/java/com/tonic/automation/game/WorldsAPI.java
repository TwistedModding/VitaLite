package com.tonic.automation.game;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.queries.WorldQuery;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

public class WorldsAPI
{
    public static World getCurrentWorld()
    {
        Client client = Static.getClient();
        return new WorldQuery().withId(client.getWorld()).first();
    }

    public static void hopRandomMembers()
    {
        World world = new WorldQuery().isP2p().notSkillTotalWorlds().isMainGame().notPvp().random();
        hop(world);
    }

    public static void hopRandomF2p()
    {
        World world = new WorldQuery().isF2p().notSkillTotalWorlds().isMainGame().notPvp().random();
        hop(world);
    }

    public static void hop(int worldId)
    {
        WorldResult worldResult = Static.getInjector().getInstance(WorldService.class).getWorlds();
        if (worldResult == null)
            return;
        // Don't try to hop if the world doesn't exist
        World world = worldResult.findWorld(worldId);
        if (world == null)
        {
            return;
        }

        hop(world);
    }

    public static void hopTo(World world)
    {
        // this is called from the panel, on edt
        hop(world);
    }

    public static void hop(World world)
    {
        Client client = Static.getClient();
        TClient tClient = Static.getTClient();
        Static.invoke(() -> {
            if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null) {
                tClient.getPacketWriter().clickPacket(0, -1, -1);
                tClient.getPacketWriter().widgetActionPacket(1, 11927555, -1, -1);
            }
            final net.runelite.api.World rsWorld = client.createWorld();
            rsWorld.setActivity(world.getActivity());
            rsWorld.setAddress(world.getAddress());
            rsWorld.setId(world.getId());
            rsWorld.setPlayerCount(world.getPlayers());
            rsWorld.setLocation(world.getLocation());
            rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

            if (client.getGameState() == GameState.LOGIN_SCREEN)
            {
                client.changeWorld(rsWorld);
            }
        });
    }
}

package com.tonic.services;

import com.tonic.Static;
import com.tonic.types.TileItemEx;
import com.tonic.types.TileObjectEx;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class GameCache
{
    //static api
    public static int getTickCount()
    {
        return INSTANCE.tickCount;
    }

    public static Actor getInteracting()
    {
        Client client = Static.getClient();
        Actor interacting = client.getLocalPlayer().getInteracting();
        if(interacting == null)
            interacting = INSTANCE.lastInteracting;
        return interacting;
    }

    public static Stream<Player> playerStream()
    {
        synchronized (INSTANCE.playerCache)
        {
            return new ArrayList<>(INSTANCE.playerCache).stream();
        }
    }

    public static Stream<NPC> npcStream()
    {
        synchronized (INSTANCE.npcCache)
        {
            return new ArrayList<>(INSTANCE.npcCache).stream();
        }
    }

    public static ArrayList<Player> playerList()
    {
        synchronized (INSTANCE.playerCache)
        {
            return new ArrayList<>(INSTANCE.playerCache);
        }
    }

    public static ArrayList<NPC> npcList()
    {
        synchronized (INSTANCE.npcCache)
        {
            return new ArrayList<>(INSTANCE.npcCache);
        }
    }

    public static Stream<TileObjectEx> objectStream()
    {
        synchronized (INSTANCE.objectCache)
        {
            return new ArrayList<>(INSTANCE.objectCache).stream();
        }
    }

    public static ArrayList<TileObjectEx> objectList()
    {
        synchronized (INSTANCE.objectCache)
        {
            return new ArrayList<>(INSTANCE.objectCache);
        }
    }

    public static Stream<TileItemEx> tileItemStream()
    {
        synchronized (INSTANCE.tileItemCache)
        {
            return new ArrayList<>(INSTANCE.tileItemCache).stream();
        }
    }

    public static ArrayList<TileItemEx> tileItemList()
    {
        synchronized (INSTANCE.tileItemCache)
        {
            return new ArrayList<>(INSTANCE.tileItemCache);
        }
    }


    //singleton instance
    private final static GameCache INSTANCE = new GameCache();

    //trigger static initialization
    public static void init()
    {
        INSTANCE.tickCount = 0;
    }

    private GameCache()
    {
        Static.getRuneLite()
                .getEventBus()
                .register(this);
        System.out.println("GameCache initialized");
    }

    private final List<TileObjectEx> objectCache = Collections.synchronizedList(new ArrayList<>());
    private final List<TileItemEx> tileItemCache = Collections.synchronizedList(new ArrayList<>());
    private final List<NPC> npcCache = Collections.synchronizedList(new ArrayList<>());
    private final List<Player> playerCache = Collections.synchronizedList(new ArrayList<>());
    private Actor lastInteracting = null;

    private int tickCount = 0;

    @Subscribe
    public void onGameTick(GameTick event)
    {
        tickCount++;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if(event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
            tickCount = 0;
    }

    // ############## Actors ##############

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event)
    {
        synchronized (playerCache)
        {
            playerCache.add(event.getPlayer());
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        synchronized (npcCache)
        {
            npcCache.add(event.getNpc());
        }
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned event)
    {
        synchronized (playerCache)
        {
            playerCache.remove(event.getPlayer());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        synchronized (npcCache)
        {
            npcCache.remove(event.getNpc());
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        Client client = Static.getClient();
        if(event.getSource() == client.getLocalPlayer() && event.getTarget() != null)
        {
            if(filter(event.getSource()))
                lastInteracting = event.getTarget();
        }
        else if(event.getTarget() == client.getLocalPlayer() && event.getSource() != null)
        {
            if(filter(event.getSource()))
                lastInteracting = event.getSource();
        }
    }

    private boolean filter(Actor actor)
    {
        return actor.getName() != null;
    }

    // ############## TileObjects ##############

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        addTileObject(event.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        removeTileObject(event.getGameObject());
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        addTileObject(event.getWallObject());
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event)
    {
        removeTileObject(event.getWallObject());
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        addTileObject(event.getDecorativeObject());
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
    {
        removeTileObject(event.getDecorativeObject());
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        addTileObject(event.getGroundObject());
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event)
    {
        removeTileObject(event.getGroundObject());
    }

    private void addTileObject(TileObject tileObject)
    {
        synchronized (objectCache)
        {
            objectCache.add(new TileObjectEx(tileObject));
        }
    }

    private void removeTileObject(TileObject tileObject)
    {
        synchronized (objectCache)
        {
            Iterator<TileObjectEx> iterator = objectCache.iterator();
            while (iterator.hasNext())
            {
                TileObjectEx obj = iterator.next();
                if (obj.getTileObject().equals(tileObject))
                {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    //tile items

    @Subscribe
    public void onItemSpawned(ItemSpawned event)
    {
        synchronized (tileItemCache)
        {
            tileItemCache.add(
                    new TileItemEx(
                            event.getItem(),
                            WorldPoint.fromLocal(Static.getClient(), event.getTile().getLocalLocation()),
                            event.getTile().getLocalLocation()
                    )
            );
        }
    }
}

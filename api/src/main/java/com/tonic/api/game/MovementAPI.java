package com.tonic.api.game;

import com.tonic.Static;
import com.tonic.api.TClient;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import java.util.List;
import java.util.Random;

public class MovementAPI {
    public static final Random random = new Random();
    private static final int STAMINA_VARBIT = 25;
    private static final int RUN_VARP = 173;

    public static WorldPoint getDestinationWorldPoint()
    {
        Client client = Static.getClient();
        LocalPoint lp = client.getLocalDestinationLocation();
        if (lp == null)
        {
            return null;
        }
        return WorldPoint.fromLocal(client, lp);
    }

    public static boolean isRunEnabled()
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getVarpValue(RUN_VARP)) == 1;
    }

    public static boolean staminaInEffect()
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getVarbitValue(STAMINA_VARBIT)) > 0;
    }

    public static boolean isMoving()
    {
        Client client = Static.getClient();
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        WorldPoint dest = getDestinationWorldPoint();
        if(dest == null)
            return false;

        return wp.distanceTo(dest) >= 1;
    }

    public static void walkToWorldPoint(WorldPoint worldPoint)
    {
        walkToWorldPoint(worldPoint.getX(), worldPoint.getY());
    }

    public static void walkToWorldPoint(int worldX, int worldY)
    {
        TClient client = Static.getTClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().walkPacket(worldX, worldY, false);
        });
    }

    public static void walkAproxWorldPoint(WorldPoint worldPoint, int radius)
    {

        int x = random.nextInt(radius * 2) + worldPoint.getX();
        int y = random.nextInt(radius * 2) + worldPoint.getY();
        walkToWorldPoint(x, y);
    }

    public static void walkRelativeToWorldPoint(int offsetX, int offsetY)
    {
        Client client = Static.getClient();
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        walkToWorldPoint(wp.getX() + offsetX, wp.getY() + offsetY);
    }

    public static boolean walkTowards(WorldPoint worldPoint)
    {
        Client client = Static.getClient();
        if(client.getLocalPlayer() == null || worldPoint == null)
            return false;
        if (client.getLocalPlayer().getWorldLocation().distanceTo(worldPoint) > 100) {
            WorldView worldView = client.getTopLevelWorldView();
            WorldPoint local = client.getLocalPlayer().getWorldLocation();
            int distance = client.getLocalPlayer().getWorldLocation().distanceTo(worldPoint);
            int ratio = 100 / distance;
            int xDiff = worldPoint.getX() - local.getX();
            int yDiff = worldPoint.getY() - local.getY();
            int newX = (int) Math.round(local.getX() + xDiff * ratio + (yDiff * 0.75));
            int newY = (int) Math.round(local.getY() + yDiff * ratio + (xDiff * 0.75));
            WorldPoint nwp = new WorldPoint(newX, newY, worldView.getPlane());
            walkToWorldPoint(nwp);
            return false;
        }
        walkToWorldPoint(worldPoint);
        return true;
    }

    public static void cardinalWalk(WorldPoint target)
    {
        Client client = Static.getClient();
        WorldPoint local = client.getLocalPlayer().getWorldLocation();
        int x = target.getX();
        int y = target.getY();
        int plane = local.getPlane();
        WorldPoint dest = null;
        while(dest == null || !SceneAPI.isReachable(local, dest))
        {
            if(target.getX() > local.getX())
            {
                x++;
            }
            else if(target.getX() < local.getX())
            {
                x--;
            }
            if(target.getY() > local.getY())
            {
                y++;
            }
            else if(target.getY() < local.getY())
            {
                y--;
            }
            dest = new WorldPoint(x, y, plane);
        }
        walkToWorldPoint(dest);
    }

    public static void cardinalWalk(WorldArea target, int distance) {
        Client client = Static.getClient();
        WorldPoint localPlayer = client.getLocalPlayer().getWorldLocation();
        int x = target.getX();
        int y = target.getY();
        int width = target.getWidth();
        int height = target.getHeight();
        int plane = target.getPlane();
        WorldPoint dest = null;

        // Define the bounds of the NPC's area
        int leftBound = x;
        int rightBound = x + width;
        int bottomBound = y;
        int topBound = y + height;

        // Define potential cardinal direction movements
        int[][] directions = {
                {0, -1}, // south
                {0, 1},  // north
                {-1, 0}, // west
                {1, 0}   // east
        };

        // Iterate over cardinal directions to find a valid destination
        for (int[] dir : directions) {
            int newX = localPlayer.getX() + dir[0] * distance;
            int newY = localPlayer.getY() + dir[1] * distance;

            // Ensure the destination is outside the NPC's area and within the specified distance
            if ((newX < leftBound - distance || newX > rightBound + distance || newY < bottomBound - distance || newY > topBound + distance) &&
                    (newX != localPlayer.getX() || newY != localPlayer.getY())) {

                // Create a new potential destination WorldPoint
                dest = new WorldPoint(newX, newY, plane);

                // Check if the destination is reachable
                if (SceneAPI.isReachable(localPlayer, dest)) {
                    break;
                }

                // Reset destination if not reachable
                dest = null;
            }
        }

        // If a valid destination is found, move the player
        if (dest != null) {
            walkToWorldPoint(dest);
        }
    }

    public static boolean canPathTo(WorldPoint current, WorldPoint target)
    {
        List<WorldPoint> pathTo = SceneAPI.pathTo(current, target);
        return pathTo != null && pathTo.contains(target);
    }
}

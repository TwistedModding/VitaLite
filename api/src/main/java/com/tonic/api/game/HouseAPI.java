package com.tonic.api.game;

import com.tonic.data.WorldLocation;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class HouseAPI {
    public static final int HOUSE_LOCATION_VARBIT = 2187;
    public static final WorldLocation[] HOUSE_LOCATIONS = {
            WorldLocation.RIMMINGTON_PORTAL,
            WorldLocation.TAVERLY_POH_PORTAL,
            WorldLocation.POLLNIVNEACH_POH_PORTAL,
            WorldLocation.RELLEKKA_POH_PORTAL,
            WorldLocation.BRIMHAVEN_POH_PORTAL,
            WorldLocation.YANILLE_POH_PORTAL,
            WorldLocation.PRIFDDINAS_POH_PORTAL,
            WorldLocation.HOSIDIUS_POH_PORTAL
    };

    public static WorldPoint getOutsideLocation()
    {
        if (!GameAPI.isLoggedIn())
        {
            return null;
        }

        int idx = VarAPI.getVar(HOUSE_LOCATION_VARBIT);
        if (idx >= HOUSE_LOCATIONS.length || idx <= 0)
        {
            return null;
        }

        return getCenter(HOUSE_LOCATIONS[idx - 1].getWorldArea());
    }

    private static WorldPoint getCenter(WorldArea area)
    {
        int x = area.getX();
        int y = area.getY();
        int width = area.getWidth();
        int height = area.getHeight();
        int plane = area.getPlane();
        return new WorldPoint(x + (width / 2), y + (height / 2), plane);
    }
}

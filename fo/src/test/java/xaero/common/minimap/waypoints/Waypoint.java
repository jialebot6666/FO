package xaero.common.minimap.waypoints;

import xaero.hud.minimap.waypoint.WaypointColor;

/** 与真实 Xaero 的 {@code Waypoint} 同签名。 */
public class Waypoint {
    private final int x;
    private final int y;
    private final int z;
    private final String name;
    private final String symbol;
    private final WaypointColor color;
    private final int colorIndex;

    public Waypoint(int x, int y, int z, String name, String symbol, WaypointColor color) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.symbol = symbol;
        this.color = color;
        this.colorIndex = color == null ? -1 : color.ordinal();
    }

    public Waypoint(int x, int y, int z, String name, String symbol, int color, int type, boolean oneoff) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.symbol = symbol;
        this.color = null;
        this.colorIndex = color;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public WaypointColor getWaypointColor() {
        return color;
    }

    public int getColorIndex() {
        return colorIndex;
    }
}

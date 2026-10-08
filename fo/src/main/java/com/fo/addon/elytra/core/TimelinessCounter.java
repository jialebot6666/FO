package com.fo.addon.elytra.core;

public final class TimelinessCounter {
    private final int window;
    private int count;
    private long lastTick = -4611686018427387904L;

    public TimelinessCounter(int window) {
        this.window = Math.max(1, window);
    }

    public int window() {
        return this.window;
    }

    public void accumulate(int now) {
        this.count = (long)now < this.lastTick || (long)now - this.lastTick > (long)this.window ? 1 : ++this.count;
        this.lastTick = now;
    }

    public int getCount(int now) {
        if ((long)now < this.lastTick || (long)now - this.lastTick > (long)this.window) {
            return 0;
        }
        return this.count;
    }

    public boolean reached(int now, int limit) {
        return this.getCount(now) >= limit;
    }

    public void clear(int now) {
        this.count = 0;
        this.lastTick = now;
    }

    public void reset() {
        this.count = 0;
        this.lastTick = -4611686018427387904L;
    }
}


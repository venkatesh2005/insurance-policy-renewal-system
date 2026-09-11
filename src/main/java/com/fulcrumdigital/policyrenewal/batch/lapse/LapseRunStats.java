package com.fulcrumdigital.policyrenewal.batch.lapse;

import java.util.concurrent.atomic.AtomicInteger;

public class LapseRunStats {

    private static final AtomicInteger lapsed  = new AtomicInteger(0);
    private static final AtomicInteger skipped = new AtomicInteger(0);

    public static void reset()          { lapsed.set(0); skipped.set(0); }
    public static void recordLapsed()   { lapsed.incrementAndGet(); }
    public static void recordSkipped()  { skipped.incrementAndGet(); }
    public static int  getLapsed()      { return lapsed.get(); }
    public static int  getSkipped()     { return skipped.get(); }
}

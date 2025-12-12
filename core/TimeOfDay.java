package com.mineshaft.core;

/**
 * ✅ ULTRA OPTIMIZED TimeOfDay - NO LAG
 * - Cached color calculations
 * - Reduced update frequency
 * - Pre-calculated values
 * - Brighter night (0.6f instead of 0.3f)
 */
public class TimeOfDay {
    private static final int TICKS_PER_DAY = 24000;

    private long worldTime = 0;
    private float daySpeed = 1.0f;

    // Sky color presets
    private final float[] skyColorDay = { 0.5f, 0.7f, 1.0f };
    private final float[] skyColorNight = { 0.05f, 0.05f, 0.15f };
    private final float[] skyColorSunrise = { 1.0f, 0.5f, 0.3f };
    private final float[] skyColorSunset = { 1.0f, 0.4f, 0.2f };

    private final float[] currentSkyColor = new float[3];
    private final float[] currentFogColor = new float[3];

    // ✅ OPTIMIZATION: Cache values
    private long lastUpdateTime = -1000; // Force first update
    private int cachedSkylightLevel = 15;
    private float cachedBrightness = 1.0f;
    private float cachedCelestialAngle = 0.0f;

    // ✅ OPTIMIZATION: Update frequency control
    private int updateCounter = 0;
    private static final int UPDATE_INTERVAL = 5; // Update colors every 5 ticks

    public TimeOfDay() {
        updateColors();
        updateCachedValues();
    }

    /**
     * ✅ OPTIMIZED: Only update when needed
     */
    public void update() {
        worldTime += (long) daySpeed;
        updateCounter++;

        // ✅ Only update colors every N ticks (not every frame!)
        if (updateCounter >= UPDATE_INTERVAL) {
            updateCounter = 0;
            updateColors();
            updateCachedValues();
        }
    }

    /**
     * ✅ OPTIMIZED: Cache expensive calculations
     */
    private void updateCachedValues() {
        long time = getTimeOfDay();

        // Only recalculate if time changed significantly
        if (Math.abs(time - lastUpdateTime) < 10) {
            return;
        }

        int oldSkylightLevel = cachedSkylightLevel;
        float oldBrightness = cachedBrightness;

        lastUpdateTime = time;
        cachedSkylightLevel = calculateSkylightLevel(time);
        cachedBrightness = calculateBrightness(time); // ✅ UPDATED: Use new brightness calculation

        long offset = (time - 6000 + TICKS_PER_DAY) % TICKS_PER_DAY;
        cachedCelestialAngle = offset / (float) TICKS_PER_DAY;

        // ✅ DEBUG: Log when brightness changes significantly
        if (Math.abs(cachedBrightness - oldBrightness) > 0.05f || cachedSkylightLevel != oldSkylightLevel) {
            System.out.printf("[TimeOfDay] Time: %d | Sky Light: %d | Brightness: %.3f | Phase: %s%n",
                    time, cachedSkylightLevel, cachedBrightness, getTimePhase());
        }
    }

    public long getTimeOfDay() {
        return worldTime % TICKS_PER_DAY;
    }

    public long getDayCount() {
        return worldTime / TICKS_PER_DAY;
    }

    public int getMoonPhase() {
        return (int) (getDayCount() % 8);
    }

    /**
     * ✅ OPTIMIZED: Return cached value
     */
    public int getSkylightLevel() {
        return cachedSkylightLevel;
    }

    /**
     * ✅ Calculate skylight (called only when needed)
     */
    private int calculateSkylightLevel(long time) {
        if (time >= 0 && time < 1000) {
            float t = time / 1000.0f;
            return (int) (4 + t * 11);
        } else if (time >= 1000 && time < 12000) {
            return 15;
        } else if (time >= 12000 && time < 13000) {
            float t = (time - 12000) / 1000.0f;
            return (int) (15 - t * 11);
        } else {
            return 4;
        }
    }

    /**
     * ✅ MINECRAFT-STYLE: Brightness calculation based on sky light level
     * 
     * Brightness multiplier is calculated from current sky light level:
     * - Sky light 15 (day) → brightness 1.0 (100% bright)
     * - Sky light 4 (night) → brightness ~0.27 (27% bright)
     * 
     * This matches Minecraft's approach where brightness = skylight / 15
     * with smooth interpolation during sunrise/sunset transitions.
     */
    private float calculateBrightness(long time) {
        // Get current sky light level (15 day, 4 night, transitions in between)
        int skylight = calculateSkylightLevel(time);

        // Base brightness from sky light level
        // Formula: brightness = skylight / 15.0
        // This gives us: 15 → 1.0, 4 → 0.267
        float baseBrightness = skylight / 15.0f;

        // Apply smooth interpolation during sunrise/sunset for better visuals
        if (time >= 0 && time < 1000) {
            // Sunrise transition (smoother curve)
            float t = time / 1000.0f;
            // Ease-in curve for smoother sunrise
            t = t * t; // quadratic ease
            float nightBrightness = 4.0f / 15.0f; // 0.267
            float dayBrightness = 1.0f;
            return nightBrightness + (t * (dayBrightness - nightBrightness));
        } else if (time >= 12000 && time < 13000) {
            // Sunset transition (smoother curve)
            float t = (time - 12000) / 1000.0f;
            // Ease-out curve for smoother sunset
            t = 1.0f - ((1.0f - t) * (1.0f - t)); // quadratic ease
            float dayBrightness = 1.0f;
            float nightBrightness = 4.0f / 15.0f; // 0.267
            return dayBrightness - (t * (dayBrightness - nightBrightness));
        }

        // For stable periods (full day/night), just return the base brightness
        return baseBrightness;
    }

    public String getTimePhase() {
        long time = getTimeOfDay();

        if (time < 1000)
            return "Sunrise";
        if (time < 12000)
            return "Day";
        if (time < 13000)
            return "Sunset";
        return "Night";
    }

    public String getMoonPhaseName() {
        String[] phases = {
                "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
                "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"
        };
        return phases[getMoonPhase()];
    }

    /**
     * ✅ OPTIMIZED: Return cached angle
     */
    public float getCelestialAngle() {
        return cachedCelestialAngle;
    }

    /**
     * ✅ OPTIMIZED: Simplified color update (less frequent)
     */
    private void updateColors() {
        long time = getTimeOfDay();

        if (time >= 0 && time < 1000) {
            float blend = time / 1000.0f;
            lerp(currentSkyColor, skyColorNight, skyColorSunrise, blend);
        } else if (time >= 1000 && time < 3000) {
            float blend = (time - 1000) / 2000.0f;
            lerp(currentSkyColor, skyColorSunrise, skyColorDay, blend);
        } else if (time >= 3000 && time < 12000) {
            System.arraycopy(skyColorDay, 0, currentSkyColor, 0, 3);
        } else if (time >= 12000 && time < 13000) {
            float blend = (time - 12000) / 1000.0f;
            lerp(currentSkyColor, skyColorDay, skyColorSunset, blend);
        } else if (time >= 13000 && time < 14000) {
            float blend = (time - 13000) / 1000.0f;
            lerp(currentSkyColor, skyColorSunset, skyColorNight, blend);
        } else {
            System.arraycopy(skyColorNight, 0, currentSkyColor, 0, 3);
        }

        currentFogColor[0] = currentSkyColor[0] * 0.9f;
        currentFogColor[1] = currentSkyColor[1] * 0.9f;
        currentFogColor[2] = currentSkyColor[2] * 0.9f;
    }

    private void lerp(float[] out, float[] a, float[] b, float t) {
        out[0] = a[0] + (b[0] - a[0]) * t;
        out[1] = a[1] + (b[1] - a[1]) * t;
        out[2] = a[2] + (b[2] - a[2]) * t;
    }

    public float[] getSkyColor() {
        return currentSkyColor;
    }

    public float[] getFogColor() {
        return currentFogColor;
    }

    /**
     * ✅ OPTIMIZED: Return cached brightness (now using brighter night values)
     */
    public float getBrightness() {
        return cachedBrightness;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public void setTimeOfDay(long time) {
        this.worldTime = time;
        lastUpdateTime = -1000; // Force update
        updateColors();
        updateCachedValues();
    }

    public void setDaySpeed(float speed) {
        this.daySpeed = speed;
    }

    public float getDaySpeed() {
        return daySpeed;
    }

    public void setTimeToNoon() {
        setTimeOfDay((getDayCount() * TICKS_PER_DAY) + 6000);
    }

    public void setTimeToMidnight() {
        setTimeOfDay((getDayCount() * TICKS_PER_DAY) + 18000);
    }

    public void setTimeToSunrise() {
        setTimeOfDay((getDayCount() * TICKS_PER_DAY) + 0);
    }

    public void setTimeToSunset() {
        setTimeOfDay((getDayCount() * TICKS_PER_DAY) + 12000);
    }

    public float getSunAngle() {
        return cachedCelestialAngle * 360.0f;
    }

    public float getMoonAngle() {
        return (cachedCelestialAngle * 360.0f + 180.0f) % 360.0f;
    }

    public boolean isDay() {
        long time = getTimeOfDay();
        return time >= 1000 && time < 12000;
    }

    public boolean isNight() {
        long time = getTimeOfDay();
        return time >= 13000 || time < 1000;
    }

    public boolean isSunVisible() {
        long time = getTimeOfDay();
        return time < 13000;
    }

    public boolean isMoonVisible() {
        long time = getTimeOfDay();
        return time >= 12000 || time < 1000;
    }

    public float getStarBrightness() {
        int skylight = cachedSkylightLevel;

        if (skylight >= 12) {
            return 0.0f;
        } else if (skylight >= 4) {
            return (12 - skylight) / 8.0f;
        } else {
            return 1.0f;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "Time: %d/%d (Day %d) | Phase: %s | Moon: %s | Light: %d/15 | Brightness: %.2f",
                getTimeOfDay(), TICKS_PER_DAY, getDayCount(),
                getTimePhase(), getMoonPhaseName(), cachedSkylightLevel, cachedBrightness);
    }
}
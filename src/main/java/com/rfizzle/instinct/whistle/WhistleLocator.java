package com.rfizzle.instinct.whistle;

/**
 * The lost-pet locator's pure geometry and text-key logic ({@code design/SPEC.md} §6), with no
 * Minecraft types — the {@code mc-mod-testing} Tier-1 seam alongside {@link WhistleRules}. The live
 * handler in {@link WhistleActions} reads each distant pet's offset from the player and passes the
 * raw doubles through {@link #bearing} and {@link #roundedBlocks}, and chooses its feedback line
 * through {@link #lineKey} — so the compass, distance, and cross-dimension formatting decisions are
 * unit-testable without a server.
 */
public final class WhistleLocator {

    /** At most this many pet lines print per locate; the rest collapse into an "…and N more." line. */
    public static final int MAX_LINES = 10;

    private WhistleLocator() {
    }

    /** The eight compass points a distant pet's bearing rounds to, each with its localized word key. */
    public enum Compass8 {
        N("n"), NE("ne"), E("e"), SE("se"), S("s"), SW("sw"), W("w"), NW("nw");

        private final String suffix;

        Compass8(String suffix) {
            this.suffix = suffix;
        }

        public String langKey() {
            return "notification.instinct.whistle.locate.dir." + suffix;
        }
    }

    /** A located pet's posture, as the locator reports it. */
    public enum PetState {
        SITTING("sitting"), FOLLOWING("following"), GUARDING("guarding"), DOWNED("downed");

        private final String suffix;

        PetState(String suffix) {
            this.suffix = suffix;
        }

        public String langKey() {
            return "notification.instinct.whistle.locate.state." + suffix;
        }
    }

    /**
     * The compass point from the player toward a pet, from the pet's horizontal offset. Minecraft
     * axes: {@code +x} is east, {@code +z} is south, so north is {@code -z}. The bearing rounds to the
     * nearest of eight 45° sectors; a pet directly on the player (a zero offset) reports north.
     */
    public static Compass8 bearing(double dx, double dz) {
        if (dx == 0.0 && dz == 0.0) {
            return Compass8.N; // a pet on top of the player has no bearing; default to north
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east, 180 = south, -90 = west
        int sector = (int) Math.round(degrees / 45.0);
        sector = ((sector % 8) + 8) % 8;
        return Compass8.values()[sector];
    }

    /** A pet's distance rounded to whole blocks for the "{@code 240m}" reading (metres = blocks). */
    public static int roundedBlocks(double distance) {
        return (int) Math.round(distance);
    }

    /**
     * The feedback line key for a located pet. A same-dimension pet always carries a distance, bearing,
     * and posture, so its posture rides the line as an argument. A cross-dimension pet reports only its
     * dimension (a bearing is meaningless across dimensions), flagging a downed one with a separate line
     * so the patient still stands out.
     */
    public static String lineKey(boolean sameDimension, boolean downed) {
        if (sameDimension) {
            return "notification.instinct.whistle.locate.line";
        }
        return downed
                ? "notification.instinct.whistle.locate.line_other_downed"
                : "notification.instinct.whistle.locate.line_other";
    }
}

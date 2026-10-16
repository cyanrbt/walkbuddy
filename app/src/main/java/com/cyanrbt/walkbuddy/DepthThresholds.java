package com.cyanrbt.walkbuddy;

/**
 * Experimental depth thresholds for Find X8 Ultra upright chest-hold walking.
 * Every value here is a calibration target: change only after the checklist in
 * docs/深度阈值标定.md records a measurement, never from a single impression.
 */
final class DepthThresholds {
    /** Grid samples used for occupancy; finer grids cost more per frame. */
    static final int GRID_WIDTH = 64, GRID_HEIGHT = 96;
    /** Minimum per-pixel depth confidence from ARCore raw depth. */
    static final float CONFIDENCE_MIN = 0.65f;
    /** Forward distance band (meters) considered walkable risk. */
    static final float FORWARD_MIN = 0.2f, FORWARD_MAX = 4f;
    /** Height above the confirmed ground plane that counts as a positive obstacle. */
    static final float ABOVE_GROUND_MIN = 0.10f, ABOVE_GROUND_MAX = 2.1f;
    /** Neighbor samples farther than this in forward distance do not join one region. */
    static final float NEIGHBOR_FORWARD_JUMP = 0.35f;
    /** Connected region must have at least this many samples. */
    static final int MIN_CLUSTER_POINTS = 8;
    /** Half-width of the walk corridor (meters each side of the forward axis). */
    static final float PATH_HALF_WIDTH = 0.4f;
    /** Samples inside the corridor required before a distance is published. */
    static final int MIN_PATH_POINTS = 8;
    /** Distance grade: NEAR at or below this meters, CAUTION up to that meters. */
    static final float NEAR_METERS = 0.9f, CAUTION_METERS = 2f;
    /** Keep at most this many occupancy regions per frame. */
    static final int MAX_DETECTIONS = 12;
    /** Fresh depth samples required before occupancy is trusted. */
    static final int MIN_VALID_DEPTH_SAMPLES = 100;
    /** Ground plane must be under the phone within this height band (meters). */
    static final float GROUND_PHONE_HEIGHT_MIN = 0.85f, GROUND_PHONE_HEIGHT_MAX = 1.8f;
    /** Horizontal plane must cover at least this many square meters. */
    static final float GROUND_MIN_AREA = 0.5f;

    private DepthThresholds() {}
}

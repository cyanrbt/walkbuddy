package com.cyanrbt.walkbuddy;

import java.util.*;

/**
 * Experimental negative obstacles: holes and step-downs. Requires a confirmed ground
 * plane and an edge between floor-like samples and samples well below that plane.
 * Missing depth is never treated as a free path or as a hole by itself.
 */
final class DepthDrops {
    static List<Detection> detect(DepthObstacles.Grid g) {
        if (!g.groundKnown) return Collections.emptyList();
        int n = g.width * g.height;
        boolean[] drop = new boolean[n], floor = new boolean[n], seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!Float.isFinite(g.forward[i]) || !Float.isFinite(g.lateral[i]) || !Float.isFinite(g.aboveGround[i])
                || g.confidence[i] < DepthThresholds.CONFIDENCE_MIN
                || g.forward[i] < DepthThresholds.FORWARD_MIN || g.forward[i] > DepthThresholds.FORWARD_MAX) continue;
            if (g.aboveGround[i] <= DepthThresholds.DROP_BELOW_GROUND) drop[i] = true;
            else if (g.aboveGround[i] <= DepthThresholds.FLOOR_BAND) floor[i] = true;
        }
        List<Detection> result = new ArrayList<>();
        int[] queue = new int[n];
        for (int seed = 0; seed < n; seed++) {
            if (!drop[seed] || seen[seed]) continue;
            int head = 0, tail = 0;
            queue[tail++] = seed;
            seen[seed] = true;
            while (head < tail) {
                int p = queue[head++], x = p % g.width, y = p / g.width;
                int[] adjacent = {x > 0 ? p - 1 : -1, x + 1 < g.width ? p + 1 : -1, y > 0 ? p - g.width : -1, y + 1 < g.height ? p + g.width : -1};
                for (int next : adjacent) if (next >= 0 && drop[next] && !seen[next]) {
                    seen[next] = true;
                    queue[tail++] = next;
                }
            }
            if (tail < DepthThresholds.MIN_DROP_POINTS) continue;
            boolean[] pixels = new boolean[n];
            int minX = g.width, minY = g.height, maxX = 0, maxY = 0, edge = 0;
            float edgeDistance = Float.NaN;
            for (int k = 0; k < tail; k++) {
                int p = queue[k], x = p % g.width, y = p / g.width;
                pixels[p] = true;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                int[] adjacent = {x > 0 ? p - 1 : -1, x + 1 < g.width ? p + 1 : -1, y > 0 ? p - g.width : -1, y + 1 < g.height ? p + g.width : -1};
                for (int next : adjacent) if (next >= 0 && floor[next]) {
                    edge++;
                    float d = g.forward[next];
                    if (Float.isNaN(edgeDistance) || d < edgeDistance) edgeDistance = d;
                }
            }
            if (edge < DepthThresholds.MIN_FLOOR_EDGE_POINTS) continue;
            boolean inPath = inPathCorridor(g, queue, tail);
            Detection.Risk risk = Detection.Risk.CAPTURED;
            if (inPath && Float.isFinite(edgeDistance)) {
                if (edgeDistance <= DepthThresholds.NEAR_METERS) risk = Detection.Risk.NEAR;
                else if (edgeDistance <= DepthThresholds.CAUTION_METERS) risk = Detection.Risk.CAUTION;
            }
            result.add(new Detection("落差", 1,
                minX / (float) g.width, minY / (float) g.height,
                (maxX + 1f) / g.width, (maxY + 1f) / g.height,
                risk, edgeDistance,
                new ObstacleMask(g.width, g.height, pixels)));
        }
        result.sort((a, b) -> Integer.compare(b.risk.ordinal(), a.risk.ordinal()));
        return result.size() > DepthThresholds.MAX_DETECTIONS
            ? new ArrayList<>(result.subList(0, DepthThresholds.MAX_DETECTIONS)) : result;
    }

    private static boolean inPathCorridor(DepthObstacles.Grid g, int[] queue, int tail) {
        for (int k = 0; k < tail; k++) if (Math.abs(g.lateral[queue[k]]) <= DepthThresholds.PATH_HALF_WIDTH) return true;
        return false;
    }
}

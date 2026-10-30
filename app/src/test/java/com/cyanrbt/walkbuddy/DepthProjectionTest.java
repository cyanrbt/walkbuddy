package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class DepthProjectionTest {
    @Test public void unchangedFloatBitsAndDetectionsAcrossMovingFrames(){assertTrue(DepthProjectionCases.parity().startsWith("DEPTH_PROJECTION_PARITY_PASS cases=96 grid_points=589824"));}
    @Test public void originalGridCellCenters(){DepthProjectionCases.sampleCenters();}
    @Test public void missingOrPartialTransformCannotReuseOldFrame(){DepthProjectionCases.incompleteTransformCannotReuseOldCoordinates();}
    @Test public void independentCameraWorkspacesAndPublishedGrids(){DepthProjectionCases.independentWorkspaces();}
    @Test public void preservesNonfiniteAndBoundaryArithmetic(){DepthProjectionCases.boundaryMath();}
    @Test public void preservesNearPositiveAndNegativeObstacles(){DepthProjectionCases.obstacleAndDropResults();}
}

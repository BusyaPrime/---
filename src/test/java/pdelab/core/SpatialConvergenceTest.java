package pdelab.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class SpatialConvergenceTest {

    @Test
    @Tag("slow")
    public void testSpatialOrder() {
        new SpatialConvergenceVerifier().verifySpatialOrder();
    }
}

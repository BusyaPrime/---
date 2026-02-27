package pdelab.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class TemporalConvergenceTest {

    @Test
    @Tag("slow")
    public void testCrankNicolsonOrder() {
        new TemporalConvergenceVerifier().verifyCrankNicolsonOrder();
    }

    @Test
    @Tag("slow")
    public void testBackwardEulerOrder() {
        new TemporalConvergenceVerifier().verifyBackwardEulerOrder();
    }
}

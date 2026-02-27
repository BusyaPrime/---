package pdelab.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class VariableKappaConvergenceTest {

    @Test
    @Tag("slow")
    public void testSpatialOrderWithVariableKappa() {
        new VariableKappaConvergenceVerifier().verifySpatialOrderWithVariableKappa();
    }
}

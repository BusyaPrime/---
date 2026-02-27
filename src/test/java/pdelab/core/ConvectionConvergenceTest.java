package pdelab.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ConvectionConvergenceTest {

    @Test
    @Tag("slow")
    public void testConvectionUpwindOrder() {
        new ConvectionConvergenceVerifier().verifyConvectionUpwindOrder();
    }
}

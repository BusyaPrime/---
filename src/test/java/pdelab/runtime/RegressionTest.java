package pdelab.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import static org.junit.jupiter.api.Assertions.*;

public class RegressionTest {

    @Test
    public void testConstantOutputRegression(@TempDir Path tempDir) throws Exception {
        // Create a temporary fixed configuration
        String outDirStr = tempDir.toAbsolutePath().toString().replace("\\", "\\\\");
        String jsonConfig = """
                {
                  "Nx": 32,
                  "Ny": 32,
                  "Lx": 1.0,
                  "Ly": 1.0,
                  "alpha": 0.1,
                  "T": 0.05,
                  "dt": 0.01,
                  "scheme": "CN",
                  "maxIters": 1000,
                  "tol": 1e-8,
                  "threads": 2,
                  "outDir": "%s",
                  "testCase": "NON_TRIVIAL"
                }
                """.formatted(outDirStr);

        File configFile = File.createTempFile("config", ".json");
        Files.writeString(configFile.toPath(), jsonConfig);

        String[] args = new String[] { "run", "--config", configFile.getAbsolutePath() };

        // Апка обязана летать и не кидаться эксепшенами (падать нельзя).
        try {
            int exitCode = new picocli.CommandLine(new PdeLabCLI()).execute(args);
            assertEquals(0, exitCode, "Exit code should be 0");
        } catch (Exception e) {
            fail("Регрессия плюнула эксепшен: " + e.getMessage());
        }

        // ArtifactRegistry creates a subfolder run_timestamp/
        File baseDir = tempDir.toFile();
        File[] runs = baseDir.listFiles();
        assertNotNull(runs, "Base output directory must exist");
        assertTrue(runs.length > 0, "A run directory must have been created");

        // Сортируем по last modified шоб выцепить свежайший детерминистик-прогон
        Arrays.sort(runs, Comparator.comparingLong(File::lastModified));
        File latestRun = runs[runs.length - 1];
        Path metricsPath = Path.of(latestRun.getAbsolutePath(), "metrics.json");
        assertTrue(Files.exists(metricsPath), "Metrics file must exist after run");

        configFile.delete(); // tempDir сам за собой приберет (артифакты сгорят в огне)
    }

    @Test
    public void testConfigValidationRejectsBadInputs() {
        Config config = new Config(
                0, 64, 1.0, 1.0, 0.1, 0.1, 0.01, "CN", 1000, 1e-10, 0, "test_out", "NON_ZERO_DIRICHLET", "JACOBI",
                "ARITHMETIC");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::validate);
        assertTrue(ex.getMessage().contains("Nx"));
    }
}

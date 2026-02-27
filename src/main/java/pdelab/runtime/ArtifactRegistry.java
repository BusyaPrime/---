package pdelab.runtime;

import java.io.File;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactRegistry.class);
    private static final Pattern SENSITIVE_KEYS = Pattern.compile(".*(TOKEN|KEY|SECRET|PASS).*",
            Pattern.CASE_INSENSITIVE);

    private final String runId;
    private final File outDir;

    public ArtifactRegistry(String baseOutDir) {
        this.runId = "run_" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        this.outDir = new File(baseOutDir, runId);

        if (!this.outDir.exists() && !this.outDir.mkdirs()) {
            throw new RuntimeException("Failed to create artifact directory: " + this.outDir.getAbsolutePath());
        }
    }

    public File getPath(String filename) {
        return new File(outDir, filename);
    }

    public void dumpEnv() {
        try {
            Properties p = System.getProperties();
            java.util.Map<String, Object> env = new java.util.HashMap<>();
            env.put("java.version", p.getProperty("java.version"));
            env.put("os.name", p.getProperty("os.name"));
            env.put("os.arch", p.getProperty("os.arch"));
            env.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            env.put("timestamp", runId);
            java.util.Map<String, String> safeEnv = new java.util.HashMap<>();
            System.getenv().forEach((k, v) -> {
                if (SENSITIVE_KEYS.matcher(k).matches()) {
                    safeEnv.put(k, "***MASKED***");
                } else {
                    safeEnv.put(k, v);
                }
            });
            env.put("env", safeEnv);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(getPath("build_info.json"), env);
        } catch (IOException e) {
            logger.error("Warning: не смогли выплюнуть build_info.json. Да и черт с ним.", e);
        }
    }
}

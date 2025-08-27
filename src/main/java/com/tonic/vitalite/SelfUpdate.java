package com.tonic.vitalite;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Dimension;

/**
 * Handles self-updating of VitaLite by checking GitHub releases and downloading new versions.
 */
public final class SelfUpdate {
    
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Tonic-Box/VitaLite/releases/latest";
    private static final String USER_AGENT = "VitaLite-Updater/1.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final HttpClient httpClient;
    private final String currentJarPath;
    private final List<String> jvmArgs;
    private final List<String> programArgs;
    
    public SelfUpdate() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        
        this.currentJarPath = getCurrentJarPath();
        this.jvmArgs = getCurrentJvmArgs();
        this.programArgs = getCurrentProgramArgs();
    }
    
    /**
     * Checks for updates and performs self-update if newer version is available.
     * @return true if update was performed, false if already up to date
     * @throws IOException if update process fails
     */
    public boolean checkAndUpdate() throws IOException {
        try {
            final String latestVersion = getLatestVersionFromGitHub();
            final String currentVersion = Versioning.getVitaLiteVersion();
            
            if (currentVersion.equals(latestVersion)) {
                System.out.println("VitaLite is already up to date (v" + currentVersion + ")");
                return false;
            }
            
            // Show update dialog to user
            final boolean userConfirmed = showUpdateDialog(currentVersion, latestVersion);
            if (!userConfirmed) {
                System.out.println("Update cancelled by user");
                System.exit(0);
                return false;
            }
            
            System.out.println("Update confirmed: v" + currentVersion + " -> v" + latestVersion);
            performUpdate(latestVersion);
            return true;
            
        } catch (final Exception e) {
            throw new IOException("Self-update failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the latest release version from GitHub API.
     * @return latest version tag
     * @throws IOException if API request fails
     */
    private String getLatestVersionFromGitHub() throws IOException {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/vnd.github.v3+json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            
            final HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new IOException("GitHub API request failed: HTTP " + response.statusCode());
            }
            
            final JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            final String tagName = json.get("tag_name").getAsString();
            
            // Remove 'v' prefix if present
            return tagName.startsWith("v") ? tagName.substring(1) : tagName;
            
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Update check was interrupted", e);
        }
    }
    
    /**
     * Performs the actual update process by launching an external updater script.
     * @param newVersion the version to update to
     * @throws IOException if update process fails
     */
    private void performUpdate(final String newVersion) throws IOException {
        if (currentJarPath == null || !Files.exists(Paths.get(currentJarPath))) {
            throw new IOException("Cannot determine current JAR location for update");
        }
        
        final String downloadUrl = buildDownloadUrl(newVersion);
        final Path updateScript = createUpdateScript(downloadUrl, newVersion);
        
        System.out.println("Starting update process...");
        
        try {
            // Launch the update script and terminate current process
            final ProcessBuilder pb = new ProcessBuilder(getUpdateCommand(updateScript));
            pb.inheritIO();
            pb.start();
            System.exit(0);
            
        } catch (final Exception e) {
            // Clean up script if launch fails
            try {
                Files.deleteIfExists(updateScript);
            } catch (final IOException ignored) {}
            
            throw new IOException("Failed to launch update process", e);
        }
    }
    
    /**
     * Creates a platform-specific update script.
     * @param downloadUrl URL to download the new JAR
     * @param newVersion version being downloaded
     * @return path to the created update script
     */
    private Path createUpdateScript(final String downloadUrl, final String newVersion) throws IOException {
        final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        final String scriptName = isWindows ? "vitalite-update.bat" : "vitalite-update.sh";
        final Path scriptPath = Paths.get(System.getProperty("java.io.tmpdir"), scriptName);
        
        final StringBuilder script = new StringBuilder();
        
        if (isWindows) {
            script.append("@echo off\n");
            script.append("echo VitaLite Self-Update v").append(newVersion).append("\n");
            script.append("echo Waiting for VitaLite to close...\n");
            script.append("timeout /t 3 /nobreak >nul\n");
            script.append("echo Downloading new version...\n");
            script.append("powershell -Command \"Invoke-WebRequest -Uri '").append(downloadUrl)
                  .append("' -OutFile '").append(currentJarPath).append("'\"\n");
            script.append("echo Restarting VitaLite...\n");
            script.append(buildRestartCommand()).append("\n");
            script.append("del \"%~f0\"\n"); // Self-delete script
        } else {
            script.append("#!/bin/bash\n");
            script.append("echo \"VitaLite Self-Update v").append(newVersion).append("\"\n");
            script.append("echo \"Waiting for VitaLite to close...\"\n");
            script.append("sleep 3\n");
            script.append("echo \"Downloading new version...\"\n");
            script.append("curl -L \"").append(downloadUrl).append("\" -o \"").append(currentJarPath).append("\"\n");
            script.append("echo \"Restarting VitaLite...\"\n");
            script.append(buildRestartCommand()).append(" &\n");
            script.append("rm \"$0\"\n"); // Self-delete script
        }
        
        Files.write(scriptPath, script.toString().getBytes());
        
        if (!isWindows) {
            // Make script executable on Unix systems
            scriptPath.toFile().setExecutable(true);
        }
        
        return scriptPath;
    }
    
    /**
     * Builds the download URL for the specified version.
     * @param version version to download
     * @return download URL
     */
    private String buildDownloadUrl(final String version) {
        return "https://github.com/Tonic-Box/VitaLite/releases/download/v" + version + "/VitaLite-" + version + "-shaded.jar";
    }
    
    /**
     * Gets the command to execute the update script.
     * @param scriptPath path to the update script
     * @return command array for ProcessBuilder
     */
    private List<String> getUpdateCommand(final Path scriptPath) {
        final List<String> command = new ArrayList<>();
        final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        
        if (isWindows) {
            command.add("cmd");
            command.add("/c");
        } else {
            command.add("/bin/bash");
        }
        
        command.add(scriptPath.toString());
        return command;
    }
    
    /**
     * Builds the restart command that preserves current JVM and program arguments.
     * @return restart command string
     */
    private String buildRestartCommand() {
        final StringBuilder cmd = new StringBuilder();
        
        // Java executable
        final String javaHome = System.getProperty("java.home");
        final String javaBin = javaHome + System.getProperty("file.separator") + 
                              "bin" + System.getProperty("file.separator") + "java";
        
        cmd.append("\"").append(javaBin).append("\"");
        
        // JVM arguments
        for (final String jvmArg : jvmArgs) {
            cmd.append(" ").append(jvmArg);
        }
        
        // JAR execution
        cmd.append(" -jar \"").append(currentJarPath).append("\"");
        
        // Program arguments
        for (final String programArg : programArgs) {
            cmd.append(" ").append(programArg);
        }
        
        return cmd.toString();
    }
    
    /**
     * Determines the path to the current running JAR file.
     * @return path to current JAR, or null if not running from JAR
     */
    private String getCurrentJarPath() {
        try {
            final String jarPath = SelfUpdate.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            
            // Normalize Windows paths
            if (System.getProperty("os.name").toLowerCase().contains("windows") && 
                jarPath.startsWith("/")) {
                return jarPath.substring(1);
            }
            
            return jarPath;
        } catch (final Exception e) {
            System.err.println("Warning: Could not determine current JAR path: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the current JVM arguments from the runtime.
     * @return list of JVM arguments
     */
    private List<String> getCurrentJvmArgs() {
        final List<String> args = new ArrayList<>();
        
        try {
            // Get JVM arguments from RuntimeMXBean if available
            final java.lang.management.RuntimeMXBean runtimeMxBean = 
                    java.lang.management.ManagementFactory.getRuntimeMXBean();
            
            for (final String arg : runtimeMxBean.getInputArguments()) {
                // Filter out dangerous or temporary arguments
                if (!arg.startsWith("-agentlib:") && 
                    !arg.startsWith("-javaagent:") &&
                    !arg.contains("tmp") &&
                    !arg.contains("temp")) {
                    args.add(arg);
                }
            }
        } catch (final Exception e) {
            System.err.println("Warning: Could not retrieve JVM arguments: " + e.getMessage());
        }
        
        return args;
    }
    
    /**
     * Gets the current program arguments (placeholder - would need to be passed from main).
     * @return list of program arguments
     */
    private List<String> getCurrentProgramArgs() {
        final List<String> args = new ArrayList<>();
        
        // Add current VitaLite options
        if (Main.optionsParser.isNoPlugins()) {
            args.add("-noPlugins");
        }
        if (Main.optionsParser.isIncognito()) {
            args.add("-incognito");
        }
        if (Main.optionsParser.isMin()) {
            args.add("-min");
        }
        if (Main.optionsParser.getRsdump() != null) {
            args.add("--rsdump");
            args.add(Main.optionsParser.getRsdump());
        }
        
        return args;
    }
    
    /**
     * Validates that the update process is safe to perform.
     * @param newVersion version to update to
     * @return true if update is safe
     */
    private boolean validateUpdate(final String newVersion) {
        if (newVersion == null || newVersion.trim().isEmpty()) {
            System.err.println("Invalid version string: " + newVersion);
            return false;
        }
        
        if (currentJarPath == null) {
            System.err.println("Cannot determine current JAR location");
            return false;
        }
        
        final Path jarPath = Paths.get(currentJarPath);
        if (!Files.exists(jarPath) || !Files.isWritable(jarPath.getParent())) {
            System.err.println("JAR location is not writable: " + currentJarPath);
            return false;
        }
        
        return true;
    }
    
    /**
     * Performs a quick update check without downloading.
     * @return true if update is available
     */
    public boolean isUpdateAvailable() {
        try {
            final String latestVersion = getLatestVersionFromGitHub();
            final String currentVersion = Versioning.getVitaLiteVersion();
            
            return !currentVersion.equals(latestVersion);
        } catch (final Exception e) {
            System.err.println("Failed to check for updates: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the latest available version without updating.
     * @return latest version string or null if check fails
     */
    public String getLatestVersion() {
        try {
            return getLatestVersionFromGitHub();
        } catch (final Exception e) {
            System.err.println("Failed to get latest version: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Shows an update dialog to the user with update information.
     * @param currentVersion current version string
     * @param latestVersion latest available version string  
     * @return true if user confirms update, false if cancelled
     */
    private boolean showUpdateDialog(final String currentVersion, final String latestVersion) {
        try {
            // Create custom dialog for better control
            final JDialog dialog = new JDialog();
            dialog.setTitle("VitaLite Update Available");
            dialog.setModal(true);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.setResizable(false);
            
            // Main panel
            final JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Title label
            final JLabel titleLabel = new JLabel("Update Available", SwingConstants.CENTER);
            titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            mainPanel.add(titleLabel, BorderLayout.NORTH);
            
            // Version info panel
            final JPanel versionPanel = new JPanel(new BorderLayout(5, 5));
            final JLabel currentLabel = new JLabel("Current: v" + currentVersion, SwingConstants.CENTER);
            final JLabel latestLabel = new JLabel("Latest: v" + latestVersion, SwingConstants.CENTER);
            final JLabel infoLabel = new JLabel("<html><center>VitaLite will download and install the update,<br>then restart automatically.</center></html>", SwingConstants.CENTER);
            
            versionPanel.add(currentLabel, BorderLayout.NORTH);
            versionPanel.add(latestLabel, BorderLayout.CENTER);
            versionPanel.add(infoLabel, BorderLayout.SOUTH);
            mainPanel.add(versionPanel, BorderLayout.CENTER);
            
            // Button panel
            final JPanel buttonPanel = new JPanel(new FlowLayout());
            final JButton updateButton = new JButton("Update Now");
            final JButton cancelButton = new JButton("Cancel");
            
            final boolean[] result = {false};
            
            updateButton.addActionListener(e -> {
                result[0] = true;
                dialog.dispose();
            });
            
            cancelButton.addActionListener(e -> {
                result[0] = false;
                dialog.dispose();
            });
            
            buttonPanel.add(updateButton);
            buttonPanel.add(cancelButton);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            // Configure dialog
            dialog.add(mainPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null); // Center on screen
            
            // Set minimum size
            final Dimension minSize = new Dimension(350, 200);
            dialog.setMinimumSize(minSize);
            dialog.setPreferredSize(minSize);
            
            // Show dialog and wait for user response
            dialog.setVisible(true);
            
            return result[0];
            
        } catch (final Exception e) {
            System.err.println("Failed to show update dialog: " + e.getMessage());
            // Fallback to console confirmation
            return showConsoleConfirmation(currentVersion, latestVersion);
        }
    }
    
    /**
     * Fallback console confirmation if GUI dialog fails.
     * @param currentVersion current version
     * @param latestVersion latest version
     * @return true (auto-confirm in non-interactive mode)
     */
    private boolean showConsoleConfirmation(final String currentVersion, final String latestVersion) {
        System.out.println("=== VitaLite Update Available ===");
        System.out.println("Current: v" + currentVersion);
        System.out.println("Latest:  v" + latestVersion);
        System.out.println("VitaLite will update and restart automatically in 3 seconds...");
        
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        return true;
    }
    
    /**
     * Performs update with user confirmation.
     * @param force if true, skips confirmation prompts
     * @return true if update was performed
     */
    public boolean updateWithConfirmation(final boolean force) {
        try {
            final String latestVersion = getLatestVersionFromGitHub();
            final String currentVersion = Versioning.getVitaLiteVersion();
            
            if (currentVersion.equals(latestVersion)) {
                System.out.println("VitaLite is already up to date (v" + currentVersion + ")");
                return false;
            }
            
            if (!validateUpdate(latestVersion)) {
                System.err.println("Update validation failed - aborting");
                return false;
            }
            
            System.out.println("Update available: v" + currentVersion + " -> v" + latestVersion);
            
            if (!force) {
                final boolean userConfirmed = showUpdateDialog(currentVersion, latestVersion);
                if (!userConfirmed) {
                    System.out.println("Update cancelled by user");
                    return false;
                }
            }
            
            performUpdate(latestVersion);
            return true;
            
        } catch (final Exception e) {
            System.err.println("Self-update failed: " + e.getMessage());
            return false;
        }
    }
}
package forge.headless;

import forge.gui.GuiBase;

/**
 * Main entry point for Forge headless mode.
 */
public final class Main {
    /**
     * Main entry point for Forge headless simulation
     */
    public static void main(final String[] args) {
        // HACK - temporary solution to "Comparison method violates it's general contract!" crash
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        // Setup headless GUI interface (minimal implementation, no actual GUI)
        GuiBase.setInterface(new HeadlessGuiBase());

        if (args.length == 0) {
            System.out.println("Forge Headless Mode");
            System.out.println("Usage: java -jar forge-headless.jar sim <args>");
            System.out.println("Run 'java -jar forge-headless.jar sim' for simulation help");
            System.exit(1);
        }

        // Command line startup
        String mode = args[0].toLowerCase();

        switch (mode) {
            case "sim":
                SimulateMatch.simulate(args);
                break;

            default:
                System.out.println("Unknown mode: " + mode);
                System.out.println("Known modes: 'sim'");
                break;
        }

        System.exit(0);
    }

    // disallow instantiation
    private Main() {
    }
}

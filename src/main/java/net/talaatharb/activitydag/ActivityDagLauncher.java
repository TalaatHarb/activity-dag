package net.talaatharb.activitydag;

/** Plain main class so the fat jar can start JavaFX from the classpath. */
public final class ActivityDagLauncher {
    private ActivityDagLauncher() {
    }

    public static void main(String[] args) {
        ActivityDagApp.main(args);
    }
}

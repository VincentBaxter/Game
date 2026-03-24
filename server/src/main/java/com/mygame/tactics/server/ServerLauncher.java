package com.mygame.tactics.server;

import java.io.IOException;

/**
 * Entry point for the headless game server.
 *
 * Run this from the command line via:
 *   ./gradlew server:run
 *
 * Or build a standalone JAR and run:
 *   java -jar NewGame-server.jar
 */
public class ServerLauncher {
    public static void main(String[] args) {
        System.out.println("Starting Tactics Game Server...");

        GameServer server = new GameServer();
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Keep the server running until the process is killed.
        // Kryonet runs its network loop on a background thread so
        // the main thread just needs to stay alive.
        System.out.println("Server is running. Press Ctrl+C to stop.");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Server interrupted — shutting down.");
        }
    }
}
package me.jaffe2718.cmdkit;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.Enumeration;

public class CommandDebugDevKit implements ModInitializer {

    /**
     * The mod ID.
     */
    public static final String MOD_ID = "cmdkit";

    public static String ipv4;

    /**
     * The mod logger.
     */
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * The server socket used to listen for commands and execute them.
     * */
    public static ServerSocket executeCmdSocket = null;

    /**
     * The server socket used to listen for commands and send suggestions.
     * */
    public static ServerSocket suggestCmdSocket = null;

    /**
     * The server socket used to receive datapack files.
     * */
    public static ServerSocket manageDatapackSocket = null;

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing CommandDebugDevKit...");
        try {
            executeCmdSocket = new ServerSocket(0);
            suggestCmdSocket = new ServerSocket(0);
            manageDatapackSocket = new ServerSocket(0);
            // Get the local IP address
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<java.net.InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    java.net.InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        ipv4 = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create server socket: " + e.getMessage());
        }
    }
}

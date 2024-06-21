package me.jaffe2718.cmdkit.event;

import me.jaffe2718.cmdkit.CommandDebugDevKit;
import me.jaffe2718.cmdkit.client.CommandDebugDevKitClient;
import me.jaffe2718.cmdkit.mixins.ChatScreenMixin;
import me.jaffe2718.cmdkit.unit.TempDatapackManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public abstract class EventHandler {

    /**
     * The command {@link ChatInputSuggestor suggetor}
     */
    public static ChatInputSuggestor suggestor = null;

    /**
     * The last command that the player input in the chat screen.
     */
    public static volatile String lastInput = "";

    /**
     * The flag that indicates whether the socket injector info has been shown.
     */
    private static boolean shown = false;

    /**
     * The list of client execute sockets.
     * To manage the client execute sockets.
     */
    private static final List<Socket> clientExecuteSockets = new ArrayList<>();

    /**
     * The thread that accepts the new client execute socket.
     * To accept the client execute socket.
     */
    private static final Thread acceptExecuteSocketThread = new Thread(() -> {
        while (true) {
            try {
                Socket clientSocket = CommandDebugDevKit.executeCmdSocket.accept();
                clientExecuteSockets.add(clientSocket);
                buildClientSocketThread(clientSocket, true).start();
                CommandDebugDevKit.LOGGER.info("Client socket accepted on localhost: {}", clientSocket.getLocalPort());
            } catch (IOException e) {
                CommandDebugDevKit.LOGGER.error("Failed to accept client socket: {}", e.getMessage());
            }
        }
    });

    /**
     * The list of client suggest sockets.
     * To manage the client suggest sockets.
     */
    private static final List<Socket> clientSuggestSockets = new ArrayList<>();

    /**
     * The thread that accepts the new client suggest socket.
     * To accept the client suggest socket.
     */
    private static final Thread acceptSuggestSocketThread = new Thread(() -> {
        while (true) {
            try {
                Socket clientSocket = CommandDebugDevKit.suggestCmdSocket.accept();
                clientSuggestSockets.add(clientSocket);
                buildClientSocketThread(clientSocket, false).start();
                CommandDebugDevKit.LOGGER.info("Client socket accepted on localhost: {}", clientSocket.getLocalPort());
            } catch (IOException e) {
                CommandDebugDevKit.LOGGER.error("Failed to accept client socket: {}", e.getMessage());
            }
        }
    });

    /**
     * The thread that checks the client socket.
     * To check the client socket and remove the closed client socket.
     */
    private static final Thread checkClientSocketThread = new Thread(() -> {
        while (true) {
            for (Socket clientSocket : clientExecuteSockets) {
                if (clientSocket.isClosed() || !clientSocket.isConnected()) {
                    clientExecuteSockets.remove(clientSocket);
                    CommandDebugDevKit.LOGGER.info("Client socket removed on localhost:" + clientSocket.getLocalPort());
                }
            }
            for (Socket clientSocket : clientSuggestSockets) {
                if (clientSocket.isClosed() || !clientSocket.isConnected()) {
                    clientSuggestSockets.remove(clientSocket);
                    CommandDebugDevKit.LOGGER.info("Client socket removed on localhost:" + clientSocket.getLocalPort());
                }
            }
        }
    });

    /**
     * Registers the event handler.
     * 1. Creates a thread that gets and listens to the client socket then resolves the message from client socket.
     * 2. Registers the event handler for the client tick event: show client socket info and warning when player joins a world.
     * 3. Registers the event handler for the client receive message event: send the chat message to the client socket.
     */
    public static void register() {
        acceptExecuteSocketThread.start();
        acceptSuggestSocketThread.start();
        checkClientSocketThread.start();
        TempDatapackManager.datapackManagementSocketThread.start();
        ClientTickEvents.START_CLIENT_TICK.register(EventHandler::getSuggestor);
        ClientTickEvents.END_CLIENT_TICK.register(EventHandler::showWarning);
        ClientReceiveMessageEvents.GAME.register(EventHandler::sendLogToClientSocket);
        ServerWorldEvents.UNLOAD.register(TempDatapackManager::delateTempDatapacks);
    }

    /**
     * Builds the thread that gets and listens to the client socket then resolves the message from client socket.
     * @param clientSocket The client socket
     *                     The client socket that the thread listens to
     * @param execute The flag that indicates whether the client socket is for client execute or get suggestions
     */
    @Contract("_, _ -> new")
    private static @NotNull Thread buildClientSocketThread(Socket clientSocket, boolean execute) {
        return new Thread(() -> {
            while (clientSocket !=null && clientSocket.isConnected() && !clientSocket.isClosed()) {
                try {
                    assert MinecraftClient.getInstance().player != null;
                    BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String cmd = br.readLine().replace("\n", "");
                    if (execute) {
                        assert !cmd.isBlank();
                        MinecraftClient.getInstance().player.networkHandler.sendChatCommand(cmd);
                    } else {  // get suggestion by read and send suggestion back
                        List<String> suggestions = CommandDebugDevKitClient.getCommandSuggestions(cmd);
                        PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true);
                        for (String s : suggestions) {
                            pw.println(s);
                        }
                    }
                } catch (EOFException eof) {   // Client socket closed
                    try {
                        clientSocket.close();
                        return;
                    } catch (IOException ignored) {
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * Gets the {@link ChatInputSuggestor EventHandler.suggestor} by using a Render-Thread
     * @param client The Minecraft client.
     */
    private static void getSuggestor(@NotNull MinecraftClient client) {
        if (client.player != null && !lastInput.isEmpty()) {
            ChatScreen chatScreen = new ChatScreen("");
            ChatScreenMixin chatScreenMixin = (ChatScreenMixin) chatScreen;
            Screen currentScreen = client.currentScreen;
            client.setScreen(chatScreen);
            suggestor = chatScreenMixin.getChatInputSuggestor();
            suggestor.refresh();
            if (!lastInput.startsWith("/")) {
                chatScreenMixin.getChatField().write("/"); // + lastCommand);
            }
            // tap the command to the chat field one by one
            for (int i = 0; i < lastInput.length(); i++) {
                chatScreenMixin.getChatField().write(String.valueOf(lastInput.charAt(i)));
            }
            lastInput = "";                                  // clear the lastCommand
            client.setScreen(currentScreen);                   // switch back to the screen before
        }
    }

    /**
     * Sends the chat message back to the client socket.
     * @param chatMsg The chat message.
     * @param overlay Whether the chat message is an overlay message.
     */
    private static void sendLogToClientSocket(@NotNull Text chatMsg, boolean overlay) {
        try {
            for (Socket clientSocket : clientExecuteSockets) {
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true);
                pw.println(chatMsg.getString());
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * Shows the client socket info and warning when the player joins a world.
     * @param client The Minecraft client.
     */
    private static void showWarning(@NotNull MinecraftClient client) {
        if (client.player == null) {
            shown = false;
        } else if (!shown) {
            client.player.sendMessage(Text.of("§a§l[Command Debug Service] §r§aThe Socket Server is running"), false);

            client.player.sendMessage(Text.of("§6For Command Execution Service"), false);
            client.player.sendMessage(Text.of("  §6localhost:" + CommandDebugDevKit.executeCmdSocket.getLocalPort()), false);
            // purple
            client.player.sendMessage(Text.of("§dFor Command Suggestion Service"), false);
            client.player.sendMessage(Text.of("  §dlocalhost:" + CommandDebugDevKit.suggestCmdSocket.getLocalPort()), false);

            client.player.sendMessage(Text.of("§bFor Datapack Management Service"), false);
            client.player.sendMessage(Text.of("  §blocalhost:" + CommandDebugDevKit.receiveDatapackSocket.getLocalPort()), false);

            client.player.sendMessage(Text.of("§c§lWARNING: §r§cDo not share this host and port with untrusted clients!"), false);
            shown = true;
        }
    }
}

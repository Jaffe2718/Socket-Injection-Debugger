package me.jaffe2718.cmdkit.unit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.jaffe2718.cmdkit.CommandDebugDevKit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
//import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static java.nio.file.Files.walk;

public abstract class TempDatapackManager {
    public static List<Path> temporaryLinkedDatapacks = new ArrayList<>();  // store the path of temporary datapacks

    /**
     * Import or link a datapack.
     * @param name The name of the datapack zip file, automatically overwritten if the name already exists.
     * @param base64Data The base64 encoded zip file.
     * @param isImport Whether to import the datapack or link it.
     * */
    private static void importOrLinkDatapack(String name, String base64Data, boolean isImport) {
        final Path minecraftDir = FabricLoader.getInstance().getGameDir();
        try {
            Path datapackPath = Paths.get(minecraftDir.toString(), "saves", Objects.requireNonNull(MinecraftClient.getInstance().getServer()).getSaveProperties().getLevelName(), "datapacks", name);
            if (Files.exists(datapackPath)) {
                Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("datapack disable " + "\"file/" + name + "\"");
                Thread.sleep(10);
                Files.delete(datapackPath);
            }
            Files.write(datapackPath, Base64.getDecoder().decode(base64Data), StandardOpenOption.CREATE);
            if (!isImport && !temporaryLinkedDatapacks.contains(datapackPath)) {  // link
                temporaryLinkedDatapacks.add(datapackPath);
            }
            Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("datapack enable " + "\"file/" + name + "\"");
            Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("reload");
            // Objects.requireNonNull(MinecraftClient.getInstance().player).sendMessage(Text.of("Datapack `" + name + "` is " + (isImport ? "imported" : "linked") + "!"));
            Objects.requireNonNull(MinecraftClient.getInstance().player).sendMessage(
                    Text.translatable("message.cmdkit.word.Datapack")
                            .append("`")
                            .append(name)
                            .append("` ")
                            .append(Text.translatable("message.cmdkit.word.is"))
                            .append(Text.translatable("message.cmdkit.word." + (isImport ? "imported" : "linked")))
                            .append("!")
            );
        } catch (Exception e) {
            throw new RuntimeException("Fail to " + (isImport ? "import" : "link") + " datapack: " + e.getClass() + " "+ e.getMessage());
        }
    }

    /**
     * Delete or unlink a datapack.
     * @param name The name of the datapack zip file.
     * @param isDelete Whether to delete the datapack or unlink it.
     * */
    private static void deleteOrUnlinkDatapack(String name, boolean isDelete) {
        final Path minecraftDir = FabricLoader.getInstance().getGameDir();
        if (MinecraftClient.getInstance().getServer() != null) {
            Path datapackPath = Paths.get(minecraftDir.toString(), "saves", MinecraftClient.getInstance().getServer().getSaveProperties().getLevelName(), "datapacks", name);
            try {
                if (Files.exists(datapackPath)) {
                    if (!isDelete) {  // unlink
                        boolean isFound = false;
                        for (Path path : temporaryLinkedDatapacks) {
                            if (path.equals(datapackPath)) {
                                isFound = true;
                                temporaryLinkedDatapacks.remove(path);
                                break;
                            }
                        }
                        if (!isFound) {
                            throw new RuntimeException("Datapack `" + name + "` is not linked!");
                        }
                    }
                    Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("datapack disable " + "\"file/" + name + "\"");
                    Thread.sleep(10);
                    if (datapackPath.toFile().isDirectory()) {
                        try (Stream<Path> walk = walk(datapackPath)) {
                            walk.sorted(Comparator.reverseOrder())
                                    .forEach(path -> {
                                        try {
                                            Files.delete(path);
                                        } catch (Exception ignored) {
                                        }
                                    });
                        }
                    } else if (datapackPath.toFile().isFile()) {
                        Files.delete(datapackPath);
                    }
                    // Files.delete(datapackPath);  // may be DirectoryNotEmptyException
                    Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("reload");
                    // Objects.requireNonNull(MinecraftClient.getInstance().player).sendMessage(Text.of("Datapack `" + name + "` is " + (isDelete ? "deleted" : "unlinked") + "!"));
                    Objects.requireNonNull(MinecraftClient.getInstance().player).sendMessage(
                            Text.translatable("message.cmdkit.word.Datapack")             // `Datapack `
                                    .append("`")
                                    .append(name)
                                    .append("` ")
                                    .append(Text.translatable("message.cmdkit.word.is"))  // `is `
                                    .append(Text.translatable("message.cmdkit.word." + (isDelete ? "deleted" : "unlinked")))
                                    .append("!")
                    );
                } else {
                    throw new RuntimeException("Datapack `" + name + "` does not exist!");
                }
            } catch (Exception e) {
                throw new RuntimeException("Fail to " + (isDelete ? "delete" : "unlink") + " datapack: " + e.getClass() + " "+ e.getMessage());
            }
        } else {
            CommandDebugDevKit.LOGGER.error("Fail to delete datapack: Minecraft server is not running!");
        }
    }

    private static void queryDatapacksInfo(Socket exSocket, @NotNull MinecraftClient mcClient) {
        MinecraftServer server = mcClient.getServer();
        try {
            PrintWriter pw = new PrintWriter(exSocket.getOutputStream(), true);
            if (server == null) {
                pw.println("{\"enabled\": [], \"all\": [], \"linked\":[] }");
                return;
            }
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"enabled\": [");
            for (String datapackName : server.getDataPackManager().getEnabledIds()) {
                sb.append("\"").append(datapackName).append("\",");
            }
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("], \"all\": [");
            for (String datapackName : server.getDataPackManager().getIds()) {
                sb.append("\"").append(datapackName).append("\",");
            }
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("], \"linked\": [");
            for (Path datapackPath : temporaryLinkedDatapacks) {
                sb.append("\"").append("file/").append(datapackPath.getFileName()).append("\",");
            }
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("]}");
            pw.println(sb);
        } catch (Exception e) {
            throw new RuntimeException("Fail to query datapacks info: " + e.getClass() + " "+ e.getMessage());
        }
    }

    private static void disableDatapack(@NotNull String datapackName) {
        if (datapackName.contains(".") && !datapackName.contains("\"")) {
            datapackName = "\"file/" + datapackName + "\"";
        }
        Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("datapack disable " + datapackName);
    }

    private static void enableDatapack(@NotNull String datapackName) {
        if (datapackName.contains(".") && !datapackName.contains("\"")) {
            datapackName = "\"file/" + datapackName + "\"";
        }
        Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("datapack enable " + datapackName);
    }

    /**
     * Delete all temporary datapacks.
     * @param server The Minecraft server.
     * @param ignore The current world.
     */
    public static void delateTempDatapacks(MinecraftServer server, ServerWorld ignore) {
        for (Path datapackPath : temporaryLinkedDatapacks) {
            String datapackName = "\"file/"+ datapackPath.getFileName() + "\"";
            if (server.getDataPackManager().getEnabledIds().contains(datapackName)) {
                Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).sendChatCommand("datapack disable " + datapackName);
            }
        }
        for (Path datapackPath : temporaryLinkedDatapacks) {
            try {
                Files.delete(datapackPath);
            } catch (Exception e) {
                throw new RuntimeException("Fail to delete datapack: " + e.getClass() + " "+ e.getMessage());
            }
        }
    }

    /**
     * The thread that receives the datapack file in json format from the client socket.
     * { "name": "[datapack name].zip", "data": "[base64 encoded zip file]", "flag": "[link|import|unlink|delete|query|enable|disable]" }
     */
    public static final Thread datapackManagementSocketThread = new Thread(() -> {
        while (true) {
            try {
                Socket clientSocket = CommandDebugDevKit.receiveDatapackSocket.accept();
                CommandDebugDevKit.LOGGER.info("Client socket accepted on localhost:{}", clientSocket.getLocalPort());
                BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String jsonStr = br.readLine();
                // parse the json
                JsonObject jsonObject = new Gson().fromJson(jsonStr, JsonObject.class);
                String flag = jsonObject.get("flag").getAsString();
                String datapackName = !flag.equals("query") ? jsonObject.get("name").getAsString() : "";
                String base64EncodedZip = flag.equals("link") || flag.equals("import") ? jsonObject.get("data").getAsString() : "";
                switch (flag) {
                    case "link" -> importOrLinkDatapack(datapackName, base64EncodedZip, false);
                    case "import" -> importOrLinkDatapack(datapackName, base64EncodedZip, true);
                    case "unlink" -> deleteOrUnlinkDatapack(datapackName, false);
                    case "delete" -> deleteOrUnlinkDatapack(datapackName, true);
                    case "enable" -> enableDatapack(datapackName);
                    case "disable" -> disableDatapack(datapackName);
                    case "query" -> queryDatapacksInfo(clientSocket, MinecraftClient.getInstance());
                    default -> throw new RuntimeException("Unknown flag: " + flag);
                }
                clientSocket.close();
            } catch (Exception e) {
                CommandDebugDevKit.LOGGER.info("{} {}", e.getClass(), e.getMessage());
            }
        }
    });
}

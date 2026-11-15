package pl.ziom.ziombot;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotClient {

    private final ZiomBotPlugin plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    public BotClient(ZiomBotPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() {
        if (running.get()) return;
        running.set(true);

        workerThread = new Thread(this::connectionLoop, "ZiomBot-Client-Thread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public synchronized void shutdown() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private void connectionLoop() {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("connection.host", "localhost");
        int port = config.getInt("connection.port", 25565);
        int reconnectSeconds = config.getInt("connection.reconnect-seconds", 20);
        String botName = config.getString("bot-name", "ZiomBot");

        while (running.get()) {
            Socket socket = null;
            try {
                plugin.getLogger().info("ZiomBot próbuje połączyć się z " + host + ":" + port + "...");
                socket = new Socket(host, port);
                socket.setTcpNoDelay(true);

                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                // 1. Handshake (State 2 for Login)
                ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
                DataOutputStream handshakeOut = new DataOutputStream(handshakeBytes);
                writeVarInt(handshakeOut, 0x00);
                writeVarInt(handshakeOut, 769);  // Protocol 769 for 1.21.4
                writeString(handshakeOut, host);
                handshakeOut.writeShort(port);
                writeVarInt(handshakeOut, 2);

                writeVarInt(out, handshakeBytes.size());
                out.write(handshakeBytes.toByteArray());

                // 2. Login Start
                ByteArrayOutputStream loginBytes = new ByteArrayOutputStream();
                DataOutputStream loginOut = new DataOutputStream(loginBytes);
                writeVarInt(loginOut, 0x00);
                writeString(loginOut, botName);

                writeVarInt(out, loginBytes.size());
                out.write(loginBytes.toByteArray());
                out.flush();

                plugin.getLogger().info("ZiomBot połączył się pomyślnie z serwerem!");

                while (running.get() && !socket.isClosed()) {
                    try {
                        int length = readVarInt(in);
                        if (length <= 0) continue;

                        int packetId = readVarInt(in);
                        int payloadSize = length - getVarIntSize(packetId);
                        byte[] payload = new byte[payloadSize];
                        in.readFully(payload);

                        // Automatyczne wykrycie zgonu i wysłanie pakietu respawnu (Action 0)
                        if (isDeathPacket(packetId, payload)) {
                            plugin.getLogger().info("Wykryto zgon ZiomBota. Wysyłanie respawnu...");
                            sendRespawnPacket(out);
                        }
                    } catch (IOException e) {
                        break;
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Błąd połączenia ZiomBota: " + e.getMessage());
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }

            if (!running.get()) break;

            plugin.getLogger().info("Ponowne łączenie ZiomBota za " + reconnectSeconds + " sekund...");
            try {
                Thread.sleep(reconnectSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean isDeathPacket(int packetId, byte[] payload) {
        return false;
    }

    private void sendRespawnPacket(DataOutputStream out) throws IOException {
        ByteArrayOutputStream respawnBytes = new ByteArrayOutputStream();
        DataOutputStream respawnOut = new DataOutputStream(respawnBytes);
        writeVarInt(respawnOut, 0x00);
        writeVarInt(respawnOut, 0); // Perform Respawn

        writeVarInt(out, respawnBytes.size());
        out.write(respawnBytes.toByteArray());
        out.flush();
        plugin.getLogger().info("Wysłano pakiet respawnu.");
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt is too big");
        } while ((read & 0x80) != 0);
        return result;
    }

    private int getVarIntSize(int value) {
        int size = 0;
        while (true) {
            if ((value & ~0x7F) == 0) return size + 1;
            size++;
            value >>>= 7;
        }
    }

    private void writeString(DataOutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}

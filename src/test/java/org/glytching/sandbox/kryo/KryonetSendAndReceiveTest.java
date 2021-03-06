package org.glytching.sandbox.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import com.jayway.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * A crude Kryonet example, from a SO q+a
 */
public class KryonetSendAndReceiveTest {

    private Server server;
    private int port;
    private String response;

    @Before
    public void setUp() throws IOException {
        startServer();
    }

    @After
    public void tearDown() {
        stopServer();
    }

    @Test
    public void testPacketSending() throws IOException, InterruptedException {
        Client client = new Client();
        Kryo kryo = client.getKryo();
        kryo.register(Message.class);
        client.start();
        client.connect(1000, "localhost", port);

        client.sendTCP(new Message("RECEIVED"));

        Awaitility.await().until(() -> response != null);

        Assert.assertNotNull(response);
    }

    private void startServer() throws IOException {
        Log.set(Log.LEVEL_DEBUG);

        server = new Server();
        port = getFreePort();
        server.bind(port);

        Kryo kryo = server.getKryo();
        kryo.register(Message.class);
        Log.debug("Adding server listener");
        server.addListener(new MessageListener());
        Log.debug("Starting server... ");
        server.start();
        Log.debug("Server started successfully on port: " + port);
    }

    private int getFreePort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private class MessageListener extends Listener {

        @Override
        public void received(Connection connection, Object o) {
            Message m = (Message) o;
            response = m.message;
            Log.debug(m.message);
        }
    }

    private static class Message {
        String message;

        /// required for Kryonet deserialisation
        Message() {

        }

        Message(String message) {
            this.message = message;
        }
    }
}
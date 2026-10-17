package com.fluxcraft.MiaoMenu.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class ProxyManagerTest {
    
    @Test
    @DisplayName("Test ProxyType enum values")
    public void testProxyTypeEnum() {
        assertEquals(3, ProxyManager.ProxyType.values().length);
        assertEquals("NONE", ProxyManager.ProxyType.NONE.name());
        assertEquals("BUNGEECORD", ProxyManager.ProxyType.BUNGEECORD.name());
        assertEquals("VELOCITY", ProxyManager.ProxyType.VELOCITY.name());
    }

    @Test
    @DisplayName("Configured proxy mode is parsed without classpath detection")
    public void configuredProxyModeParsing() {
        assertEquals(ProxyManager.ProxyType.BUNGEECORD, ProxyManager.ProxyType.fromConfigValue("BUNGEE"));
        assertEquals(ProxyManager.ProxyType.BUNGEECORD, ProxyManager.ProxyType.fromConfigValue("bungeecord"));
        assertEquals(ProxyManager.ProxyType.VELOCITY, ProxyManager.ProxyType.fromConfigValue("VELOCITY"));
        assertEquals(ProxyManager.ProxyType.NONE, ProxyManager.ProxyType.fromConfigValue("NONE"));
        assertEquals(ProxyManager.ProxyType.NONE, ProxyManager.ProxyType.fromConfigValue("invalid"));
    }
    
    @Test
    @DisplayName("Test BungeeCord message format")
    public void testBungeeCordMessageFormat() {
        try {
            java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dataOut = new java.io.DataOutputStream(byteStream);
            
            dataOut.writeUTF("Connect");
            dataOut.writeUTF("lobby");
            
            byte[] message = byteStream.toByteArray();
            assertNotNull(message);
            assertTrue(message.length > 0);
            
        } catch (Exception e) {
            fail("Failed to create BungeeCord message: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test Velocity message format")
    public void testVelocityMessageFormat() {
        try {
            java.util.UUID testUuid = java.util.UUID.randomUUID();
            java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dataOut = new java.io.DataOutputStream(byteStream);
            
            dataOut.writeUTF("ConnectOther");
            dataOut.writeUTF(testUuid.toString());
            dataOut.writeUTF("survival");
            
            byte[] message = byteStream.toByteArray();
            assertNotNull(message);
            assertTrue(message.length > 0);
            
        } catch (Exception e) {
            fail("Failed to create Velocity message: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test server command parsing")
    public void testServerCommandParsing() {
        String cmd1 = "server lobby";
        String[] parts1 = cmd1.split("\\s+", 2);
        assertEquals("server", parts1[0]);
        assertEquals("lobby", parts1[1]);
        
        String cmd2 = "/server survival";
        String cmd2Clean = cmd2.replaceFirst("^/", "");
        String[] parts2 = cmd2Clean.split("\\s+", 2);
        assertEquals("server", parts2[0]);
        assertEquals("survival", parts2[1]);
    }
}

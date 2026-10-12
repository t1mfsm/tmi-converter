package ru.example.tmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Ipv4AddressTest {

    @Test
    void keepsNetworkByteOrder() {
        Ipv4Address address = Ipv4Address.fromBytes(new byte[] {0, (byte) 0xAC, 0x11, 0x72, 0x7A}, 1);

        assertEquals("172.17.114.122", address.toString());
        assertArrayEquals(new byte[] {(byte) 0xAC, 0x11, 0x72, 0x7A}, address.toBytes());
    }

    @Test
    void parsesDottedNotation() {
        assertEquals(Ipv4Address.of(255, 0, 10, 1), Ipv4Address.parse("255.0.10.1"));
    }

    @Test
    void rejectsInvalidAddresses() {
        assertThrows(IllegalArgumentException.class, () -> Ipv4Address.parse("1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> Ipv4Address.parse("1.2.3.256"));
        assertThrows(IllegalArgumentException.class, () -> Ipv4Address.parse("1.2.3.x"));
        assertThrows(IllegalArgumentException.class, () -> Ipv4Address.fromBytes(new byte[3], 0));
    }

    @Test
    void returnsDefensiveCopy() {
        Ipv4Address address = Ipv4Address.of(1, 2, 3, 4);
        address.toBytes()[0] = 99;
        assertEquals("1.2.3.4", address.toString());
    }
}

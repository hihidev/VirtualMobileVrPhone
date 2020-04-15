package dev.hihi.virtualmobilevrphone;

public class Packet {
    public byte[] bytes;
    public int size;

    public Packet(byte[] bytes, int size) {
        this.bytes = bytes;
        this.size = size;
    }
}

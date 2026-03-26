package dev.naoki.ethwhite.core;

import dev.naoki.ethwhite.crypto.Keccak;
import dev.naoki.ethwhite.util.Bytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class WorldState {
    private final NavigableMap<Address, Account> accounts;

    public WorldState() {
        this.accounts = new TreeMap<>();
    }

    private WorldState(NavigableMap<Address, Account> accounts) {
        this.accounts = accounts;
    }

    public WorldState copy() {
        NavigableMap<Address, Account> cloned = new TreeMap<>();
        accounts.forEach((address, account) -> cloned.put(address, account.copy()));
        return new WorldState(cloned);
    }

    public Account get(Address address) {
        return accounts.get(address);
    }

    public Account getOrCreate(Address address) {
        return accounts.computeIfAbsent(address, ignored -> new Account());
    }

    public boolean exists(Address address) {
        return accounts.containsKey(address);
    }

    public Collection<Map.Entry<Address, Account>> entries() {
        return accounts.entrySet();
    }

    public void restore(WorldState other) {
        accounts.clear();
        other.accounts.forEach((address, account) -> accounts.put(address, account.copy()));
    }

    public void transfer(Address from, Address to, BigInteger amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Transfer amount must not be negative");
        }
        if (amount.signum() == 0) {
            return;
        }
        getOrCreate(from).debit(amount);
        getOrCreate(to).credit(amount);
    }

    public byte[] stateRoot() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (Map.Entry<Address, Account> entry : accounts.entrySet()) {
                Address address = entry.getKey();
                Account account = entry.getValue();
                out.write(address.toBytes());
                out.write(Bytes.ofLong(account.nonce()));
                out.write(Bytes.writeLengthPrefixed(Bytes.ofBigInteger(account.balance())));
                out.write((byte) account.type().ordinal());
                out.write(Bytes.writeLengthPrefixed(account.code()));
                out.write(Bytes.writeLengthPrefixed(account.contractId() == null
                        ? new byte[0]
                        : account.contractId().getBytes(StandardCharsets.UTF_8)));
                for (Map.Entry<BigInteger, Word> storageEntry : account.storage().entrySet()) {
                    out.write(Bytes.leftPad(Bytes.ofBigInteger(storageEntry.getKey()), Word.SIZE));
                    out.write(storageEntry.getValue().toBytes());
                }
                out.write(new byte[] {(byte) 0xff});
                for (Map.Entry<String, byte[]> metadataEntry : account.metadata().entrySet()) {
                    out.write(Bytes.writeLengthPrefixed(metadataEntry.getKey().getBytes(StandardCharsets.UTF_8)));
                    out.write(Bytes.writeLengthPrefixed(metadataEntry.getValue()));
                }
                out.write(new byte[] {(byte) 0xfe});
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected byte stream error", exception);
        }
        return Keccak.hash(out.toByteArray());
    }

    public String describeBalance(Address address) {
        Account account = accounts.get(address);
        return account == null ? "0" : account.balance().toString();
    }
}

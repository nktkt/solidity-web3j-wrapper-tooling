package dev.naoki.ethwhite.core;

import dev.naoki.ethwhite.util.Bytes;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Transaction {
    private final long nonce;
    private final Address to;
    private final BigInteger value;
    private final byte[] data;
    private final long startGas;
    private final BigInteger gasPrice;

    public Transaction(long nonce, Address to, BigInteger value, byte[] data, long startGas, BigInteger gasPrice) {
        if (nonce < 0 || startGas <= 0 || gasPrice.signum() < 0 || value.signum() < 0) {
            throw new IllegalArgumentException("Transaction values must be non-negative and gas positive");
        }
        this.nonce = nonce;
        this.to = to;
        this.value = Objects.requireNonNull(value, "value");
        this.data = Arrays.copyOf(data, data.length);
        this.startGas = startGas;
        this.gasPrice = gasPrice;
    }

    public static Transaction createContract(long nonce, BigInteger value, byte[] initCode, long startGas, BigInteger gasPrice) {
        return new Transaction(nonce, null, value, initCode, startGas, gasPrice);
    }

    public long nonce() {
        return nonce;
    }

    public Address to() {
        return to;
    }

    public BigInteger value() {
        return value;
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public long startGas() {
        return startGas;
    }

    public BigInteger gasPrice() {
        return gasPrice;
    }

    public boolean isContractCreation() {
        return to == null;
    }

    public byte[] signingPayload() {
        return Bytes.joinLengthPrefixed(List.of(
                Bytes.ofLong(nonce),
                to == null ? new byte[0] : to.toBytes(),
                Bytes.ofBigInteger(value),
                data,
                Bytes.ofLong(startGas),
                Bytes.ofBigInteger(gasPrice)
        ));
    }
}

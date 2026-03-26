package dev.naoki.ethwhite.core;

import dev.naoki.ethwhite.crypto.Keccak;
import dev.naoki.ethwhite.util.Bytes;

import java.math.BigInteger;
import java.util.List;

public record BlockHeader(
        byte[] parentHash,
        byte[] stateRoot,
        byte[] transactionsRoot,
        byte[] unclesRoot,
        Address miner,
        long number,
        long timestamp,
        long difficulty,
        long gasLimit,
        long gasUsed,
        long nonce
) {
    private static final BigInteger MAX_TARGET = BigInteger.ONE.shiftLeft(255);

    public byte[] hash() {
        return Keccak.hash(Bytes.joinLengthPrefixed(List.of(
                parentHash,
                stateRoot,
                transactionsRoot,
                unclesRoot,
                miner.toBytes(),
                Bytes.ofLong(number),
                Bytes.ofLong(timestamp),
                Bytes.ofLong(difficulty),
                Bytes.ofLong(gasLimit),
                Bytes.ofLong(gasUsed),
                Bytes.ofLong(nonce)
        )));
    }

    public byte[] hashWithoutNonce() {
        return Keccak.hash(Bytes.joinLengthPrefixed(List.of(
                parentHash,
                stateRoot,
                transactionsRoot,
                unclesRoot,
                miner.toBytes(),
                Bytes.ofLong(number),
                Bytes.ofLong(timestamp),
                Bytes.ofLong(difficulty),
                Bytes.ofLong(gasLimit),
                Bytes.ofLong(gasUsed)
        )));
    }

    public boolean validProofOfWork() {
        if (difficulty <= 0) {
            return false;
        }
        BigInteger target = MAX_TARGET.divide(BigInteger.valueOf(difficulty));
        BigInteger value = new BigInteger(1, hash());
        return value.compareTo(target) <= 0;
    }

    public BlockContext blockContext() {
        return new BlockContext(number, timestamp, parentHash, miner, gasLimit, difficulty);
    }
}

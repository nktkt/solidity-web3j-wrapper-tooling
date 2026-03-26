package dev.naoki.ethwhite;

import dev.naoki.ethwhite.contract.ContractRegistry;
import dev.naoki.ethwhite.core.AccountType;
import dev.naoki.ethwhite.core.Address;
import dev.naoki.ethwhite.core.BlockContext;
import dev.naoki.ethwhite.core.CallData;
import dev.naoki.ethwhite.core.Transaction;
import dev.naoki.ethwhite.core.TransactionProcessor;
import dev.naoki.ethwhite.core.TransactionReceipt;
import dev.naoki.ethwhite.core.Units;
import dev.naoki.ethwhite.core.WorldState;
import dev.naoki.ethwhite.crypto.Wallet;
import dev.naoki.ethwhite.sample.ContractCatalog;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TransactionProcessorTest {
    @Test
    void etherTransferConsumesIntrinsicGasAndRefundsRest() {
        TransactionProcessor processor = new TransactionProcessor(new ContractRegistry());
        WorldState state = new WorldState();
        Wallet sender = Wallet.create();
        Address recipient = Address.random();
        Address miner = Address.random();
        state.getOrCreate(sender.address()).credit(Units.ETHER.multiply(BigInteger.TEN));

        Transaction transaction = new Transaction(0, recipient, Units.ETHER.multiply(BigInteger.TWO), new byte[0], 1_000, BigInteger.ONE);
        TransactionReceipt receipt = processor.apply(state, dev.naoki.ethwhite.core.SignedTransaction.sign(transaction, sender),
                new BlockContext(1, 1_700_000_000L, new byte[32], miner, 1_000_000L, 1L));

        assertTrue(receipt.success());
        assertEquals(TransactionProcessor.INTRINSIC_GAS, receipt.gasUsed());
        assertEquals(Units.ETHER.multiply(BigInteger.TWO), state.getOrCreate(recipient).balance());
        assertEquals(BigInteger.valueOf(TransactionProcessor.INTRINSIC_GAS), state.getOrCreate(miner).balance());
    }

    @Test
    void failedVmExecutionRevertsStateButBurnsGas() {
        TransactionProcessor processor = new TransactionProcessor(new ContractRegistry());
        WorldState state = new WorldState();
        Wallet owner = Wallet.create();
        Address miner = Address.random();
        state.getOrCreate(owner.address()).credit(Units.ETHER.multiply(BigInteger.TEN));

        byte[] bytecode = new byte[] {
                0x60, 0x2a,
                0x60, 0x01,
                0x55,
                0x00
        };
        Transaction deploy = Transaction.createContract(0, BigInteger.ZERO, bytecode, 1_000, BigInteger.ONE);
        TransactionReceipt deployReceipt = processor.apply(state, dev.naoki.ethwhite.core.SignedTransaction.sign(deploy, owner),
                new BlockContext(1, 1_700_000_000L, new byte[32], miner, 1_000_000L, 1L));
        assertTrue(deployReceipt.success());

        Transaction invoke = new Transaction(1, deployReceipt.contractAddress(), BigInteger.ZERO, new byte[0], 100, BigInteger.ONE);
        TransactionReceipt failure = processor.apply(state, dev.naoki.ethwhite.core.SignedTransaction.sign(invoke, owner),
                new BlockContext(2, 1_700_000_030L, new byte[32], miner, 1_000_000L, 1L));

        assertFalse(failure.success());
        assertEquals(BigInteger.ZERO, state.getOrCreate(deployReceipt.contractAddress()).storage().getOrDefault(BigInteger.ONE, dev.naoki.ethwhite.core.Word.ZERO).toBigInteger());
        assertEquals(2L, state.getOrCreate(owner.address()).nonce());
        assertEquals(BigInteger.valueOf(100 + deployReceipt.gasUsed()), state.getOrCreate(miner).balance());
    }

    @Test
    void invalidNonceIsRejected() {
        TransactionProcessor processor = new TransactionProcessor(new ContractRegistry());
        WorldState state = new WorldState();
        Wallet sender = Wallet.create();
        state.getOrCreate(sender.address()).credit(Units.ETHER);

        Transaction transaction = new Transaction(1, Address.random(), BigInteger.ONE, new byte[0], 100, BigInteger.ONE);

        assertThrows(RuntimeException.class, () -> processor.apply(state, dev.naoki.ethwhite.core.SignedTransaction.sign(transaction, sender),
                new BlockContext(1, 1_700_000_000L, new byte[32], Address.random(), 1_000_000L, 1L)));
    }
}

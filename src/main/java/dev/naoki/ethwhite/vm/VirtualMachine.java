package dev.naoki.ethwhite.vm;

import dev.naoki.ethwhite.contract.ContractContext;
import dev.naoki.ethwhite.core.Address;
import dev.naoki.ethwhite.core.ExecutionException;
import dev.naoki.ethwhite.core.OutOfGasException;
import dev.naoki.ethwhite.core.Word;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class VirtualMachine {
    public byte[] execute(byte[] code, ContractContext context) {
        Deque<Word> stack = new ArrayDeque<>();
        byte[] memory = new byte[64];
        int pc = 0;
        while (pc < code.length) {
            int current = code[pc] & 0xff;
            if (OpCode.isPush(current)) {
                int width = current - 0x5f;
                context.gasMeter().consume(3, "executing PUSH");
                pc++;
                if (pc + width > code.length) {
                    throw new ExecutionException("PUSH exceeds code bounds");
                }
                stack.push(Word.fromBytes(Arrays.copyOfRange(code, pc, pc + width)));
                pc += width;
                continue;
            }
            if (OpCode.isDup(current)) {
                int index = current - 0x7f;
                context.gasMeter().consume(2, "executing DUP");
                duplicate(stack, index);
                pc++;
                continue;
            }
            if (OpCode.isSwap(current)) {
                int index = current - 0x8f;
                context.gasMeter().consume(3, "executing SWAP");
                swap(stack, index);
                pc++;
                continue;
            }

            OpCode opCode = OpCode.fromByte(current);
            if (opCode == null) {
                throw new ExecutionException("Unknown opcode 0x" + Integer.toHexString(current));
            }
            context.gasMeter().consume(opCode.gasCost(), "executing " + opCode.name());
            switch (opCode) {
                case STOP -> {
                    return new byte[0];
                }
                case ADD -> stack.push(pop(stack).add(pop(stack)));
                case MUL -> stack.push(pop(stack).mul(pop(stack)));
                case SUB -> {
                    Word a = pop(stack);
                    Word b = pop(stack);
                    stack.push(a.sub(b));
                }
                case DIV -> {
                    Word divisor = pop(stack);
                    Word dividend = pop(stack);
                    stack.push(dividend.div(divisor));
                }
                case LT -> compare(stack, comparison(pop(stack), pop(stack)) > 0);
                case GT -> compare(stack, comparison(pop(stack), pop(stack)) < 0);
                case EQ -> compare(stack, pop(stack).equals(pop(stack)));
                case ISZERO -> compare(stack, pop(stack).isZero());
                case AND -> stack.push(Word.fromBytes(and(pop(stack).toBytes(), pop(stack).toBytes())));
                case OR -> stack.push(Word.fromBytes(or(pop(stack).toBytes(), pop(stack).toBytes())));
                case BALANCE -> {
                    Address address = Address.fromBytes(Arrays.copyOfRange(pop(stack).toBytes(), 12, 32));
                    stack.push(Word.of(context.state().getOrCreate(address).balance()));
                }
                case CALLER -> stack.push(Word.fromBytes(leftPadAddress(context.sender())));
                case CALLVALUE -> stack.push(Word.of(context.value()));
                case CALLDATALOAD -> {
                    int offset = pop(stack).toIntExact();
                    byte[] data = context.data();
                    byte[] slice = new byte[Word.SIZE];
                    if (offset < data.length) {
                        System.arraycopy(data, offset, slice, 0, Math.min(Word.SIZE, data.length - offset));
                    }
                    stack.push(Word.fromBytes(slice));
                }
                case CALLDATASIZE -> stack.push(Word.of(context.data().length));
                case PREVHASH -> stack.push(Word.fromBytes(context.block().previousBlockHash()));
                case TIMESTAMP -> stack.push(Word.of(context.block().timestamp()));
                case NUMBER -> stack.push(Word.of(context.block().number()));
                case POP -> pop(stack);
                case MLOAD -> {
                    int offset = pop(stack).toIntExact();
                    memory = ensureSize(memory, offset + Word.SIZE);
                    stack.push(Word.fromBytes(Arrays.copyOfRange(memory, offset, offset + Word.SIZE)));
                }
                case MSTORE -> {
                    int offset = pop(stack).toIntExact();
                    Word value = pop(stack);
                    memory = ensureSize(memory, offset + Word.SIZE);
                    System.arraycopy(value.toBytes(), 0, memory, offset, Word.SIZE);
                }
                case SLOAD -> {
                    Word key = pop(stack);
                    stack.push(context.storageWord(key.toBigInteger()));
                }
                case SSTORE -> {
                    Word key = pop(stack);
                    Word value = pop(stack);
                    context.putStorageWord(key.toBigInteger(), value);
                }
                case JUMP -> {
                    pc = pop(stack).toIntExact();
                    continue;
                }
                case JUMPI -> {
                    int destination = pop(stack).toIntExact();
                    Word condition = pop(stack);
                    if (!condition.isZero()) {
                        pc = destination;
                        continue;
                    }
                }
                case PC -> stack.push(Word.of(pc));
                case RETURN -> {
                    int offset = pop(stack).toIntExact();
                    int length = pop(stack).toIntExact();
                    memory = ensureSize(memory, offset + length);
                    return Arrays.copyOfRange(memory, offset, offset + length);
                }
                case REVERT -> throw new ExecutionException("Contract execution reverted");
            }
            pc++;
        }
        return new byte[0];
    }

    private static Word pop(Deque<Word> stack) {
        Word word = stack.poll();
        if (word == null) {
            throw new ExecutionException("Stack underflow");
        }
        return word;
    }

    private static void duplicate(Deque<Word> stack, int index) {
        Word[] words = stack.toArray(Word[]::new);
        if (index <= 0 || index > words.length) {
            throw new ExecutionException("Invalid DUP index");
        }
        stack.push(words[index - 1]);
    }

    private static void swap(Deque<Word> stack, int index) {
        Word[] words = stack.toArray(Word[]::new);
        if (index <= 0 || index >= words.length) {
            throw new ExecutionException("Invalid SWAP index");
        }
        Word top = words[0];
        words[0] = words[index];
        words[index] = top;
        stack.clear();
        for (int i = words.length - 1; i >= 0; i--) {
            stack.push(words[i]);
        }
    }

    private static void compare(Deque<Word> stack, boolean condition) {
        stack.push(condition ? Word.ONE : Word.ZERO);
    }

    private static int comparison(Word left, Word right) {
        return left.compareTo(right);
    }

    private static byte[] ensureSize(byte[] memory, int size) {
        if (size <= memory.length) {
            return memory;
        }
        int nextSize = Math.max(size, memory.length * 2);
        return Arrays.copyOf(memory, nextSize);
    }

    private static byte[] leftPadAddress(Address address) {
        byte[] out = new byte[Word.SIZE];
        byte[] addressBytes = address.toBytes();
        System.arraycopy(addressBytes, 0, out, Word.SIZE - addressBytes.length, addressBytes.length);
        return out;
    }

    private static byte[] and(byte[] a, byte[] b) {
        byte[] out = new byte[Math.max(a.length, b.length)];
        byte[] left = pad(out.length, a);
        byte[] right = pad(out.length, b);
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (left[i] & right[i]);
        }
        return out;
    }

    private static byte[] or(byte[] a, byte[] b) {
        byte[] out = new byte[Math.max(a.length, b.length)];
        byte[] left = pad(out.length, a);
        byte[] right = pad(out.length, b);
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (left[i] | right[i]);
        }
        return out;
    }

    private static byte[] pad(int size, byte[] input) {
        byte[] out = new byte[size];
        System.arraycopy(input, 0, out, size - input.length, input.length);
        return out;
    }
}

# eth-white

`eth-white` is a Java reference implementation inspired by the 2014 Ethereum whitepaper by Vitalik Buterin.

It is not a production Ethereum client. Instead, it is a compact, readable project that implements the major ideas described in the whitepaper:

- account-based state
- signed transactions with nonce protection
- gas accounting and fee refunds
- contract creation and contract-to-contract messages
- a small EVM-like bytecode interpreter
- block validation, proof-of-work, uncle rewards, and GHOST-style chain selection
- whitepaper application examples as native contracts

## What is implemented

### Core protocol

- Accounts with nonce, balance, code, and storage
- Transaction validation and `APPLY(S, TX) -> S'` style state transitions
- Upfront gas charging, execution metering, refunds, and revert-on-failure behavior
- Contract deployment for:
  - native Java contracts
  - bytecode contracts executed by the mini VM
- Bytecode VM with stack, memory, storage, jumps, calldata access, and return values
- Block headers, transaction roots, uncle roots, PoW validation, miner rewards, uncle rewards, and chain head selection by observed subtree weight

### Whitepaper application examples

- `token`: on-chain token balances and transfers
- `priceFeed`: trusted oracle contract for price updates
- `hedge`: a whitepaper-style derivative / stable-value hedge contract
- `nameRegistry`: name registration, update, and ownership transfer
- `fileStorage`: Merkle-proof-based storage challenge contract
- `dao`: proposal, vote, finalize, and membership mutation logic

## Security and consistency fixes included

While building the implementation, the following issues were explicitly checked and fixed:

- replay protection via strict nonce validation
- revert safety for failed contract execution and out-of-gas paths
- signature verification using raw hashed payloads instead of accidental double-hashing
- duplicate vote protection in the DAO
- uncle validation to reject already-included or canonical-ancestor blocks
- balance underflow prevention using checked `BigInteger` logic

## Project layout

```text
src/main/java/dev/naoki/ethwhite/
  core/       protocol state, transactions, gas, blocks, blockchain
  crypto/     keccak and secp256k1 helpers
  contract/   native contract execution interfaces
  vm/         small EVM-like interpreter
  sample/     whitepaper application contracts
  util/       byte, hex, and Merkle helpers
```

## Build and test

Requirements:

- Java 21+
- Maven 3.9+

Run the full test suite:

```bash
mvn test
```

Package the project:

```bash
mvn package
```

## Quick demo

Run the small token-transfer demo:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=dev.naoki.ethwhite.Main
```

If you do not want to use Maven Exec, you can also run the main class from your IDE.

## Tests included

The test suite covers:

- Ether transfers and fee accounting
- VM storage/write/return behavior
- revert-on-failure and gas burning
- token, hedge, DAO, and file-storage scenarios
- GHOST head selection and uncle/nephew rewards

## Scope notes

This project follows the whitepaper conceptually, but intentionally keeps some parts compact:

- It uses a simplified deterministic state root instead of a full Merkle Patricia Trie implementation.
- The VM is intentionally small and educational, not opcode-complete.
- The application contracts are written as native Java contracts for clarity.
- Networking, peer discovery, and real-world consensus hardening are out of scope.

That tradeoff keeps the codebase small enough to audit, extend, and study.

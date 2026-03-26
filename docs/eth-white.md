# eth-white implementation notes

This document describes the Ethereum whitepaper implementation under `src/main/java/dev/naoki/ethwhite`.

## Scope

The implementation is intentionally focused on the execution-layer ideas from the 2014 Ethereum whitepaper:

- account-based world state
- signed transactions and nonce-based replay protection
- gas accounting and revert-on-failure semantics
- contract creation and contract-to-contract messages
- an EVM-like stack machine
- block validation, proof-of-work, uncle rewards, and GHOST-style fork choice
- whitepaper application examples implemented as native contracts

It is still not a full production Ethereum node. Networking, peer discovery, disk-backed trie storage, and full opcode parity are out of scope.

## What is now implemented

### State and roots

- RLP encoding for transactions and block headers
- Hexary Patricia trie roots for:
  - account state
  - storage/metadata state
  - transaction lists
- RLP-based uncle list hashing

### Execution

- top-level contract deployment via init code
- runtime code installation from init code `RETURN`
- nested message calls
- VM support for:
  - arithmetic and comparison
  - calldata and code copying
  - storage and memory operations
  - `CALL`
  - `CREATE`
  - `SHA3`
  - environment opcodes such as `ADDRESS`, `ORIGIN`, `CALLER`, `TIMESTAMP`, `NUMBER`, `GAS`

### Native whitepaper contracts

- token systems
- price feeds
- financial hedge / stable-value example
- identity and reputation registry
- decentralized file storage challenge contract
- DAO voting and membership updates

## Security and consistency work

- fixed operand ordering bugs in VM comparison and subtraction/division paths
- made failed execution revert state while still burning fees
- switched contract-address derivation to an RLP-based sender/nonce hash
- ensured native contract identity contributes to code hashing
- kept metadata inside the trie-derived storage root so state roots reflect native contract state
- added tests for init code deployment and inter-contract `CALL`

## Verification

Run the full test suite with:

```bash
mvn test
```

The `dev.naoki.ethwhite` tests cover:

- transaction fee accounting
- init code deployment
- VM storage and return behavior
- contract-to-contract calls
- token, hedge, DAO, and file-storage flows
- GHOST selection and uncle rewards

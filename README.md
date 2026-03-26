# Java Solidity Compiler and Web3j Wrapper Generator

Production-ready Java 21 tooling for compiling Solidity contracts with the full `solc` compiler and generating Java wrappers with the Web3j CLI.

This project is designed to be explicit, deterministic, and easy to run on macOS and Linux. Java code orchestrates all external tools through `ProcessBuilder`, captures command output in detail, and fails with clear domain-specific exceptions.

## What This Project Does

- Compiles every Solidity contract under `src/main/solidity`
- Prefers a locally installed `solc` binary when available
- Falls back automatically to Docker when local `solc` is not available
- Generates ABI files
- Generates BIN files
- Generates Java wrapper classes with the Web3j CLI
- Compiles generated wrappers as part of the Gradle workflow
- Requires no blockchain node for build or test execution

## Technology Choices

- Java 21
- Gradle with Kotlin DSL
- Full Solidity compiler: `solc`
- Docker fallback image: `ghcr.io/argotorg/solc:stable`
- Web3j CLI for wrapper generation
- JUnit 5 for tests

## Project Layout

```text
.
|-- build.gradle.kts
|-- gradle/
|-- gradlew
|-- gradlew.bat
|-- settings.gradle.kts
`-- src
    |-- main
    |   |-- java
    |   |   `-- com/example/soliditycompiler
    |   `-- solidity
    |       `-- HelloWorld.sol
    `-- test
        `-- java
            `-- com/example/soliditycompiler
```

## Generated Output

The build produces the following outputs:

- ABI files: `build/generated/contracts/abi`
- BIN files: `build/generated/contracts/bin`
- Raw `solc` output: `build/generated/contracts/raw`
- Generated Java wrappers: `build/generated/source/web3j/main/java`
- Bootstrap classes for custom JavaExec tasks: `build/tooling-classes`

## Prerequisites

You need:

- Java 21
- Git
- Web3j CLI on `PATH`
- Either:
  - local `solc` on `PATH`, or
  - Docker with a working daemon

The repository includes the Gradle wrapper, so `./gradlew` is the recommended entrypoint.

## Installation Examples

### Verify Java and Gradle

```bash
java -version
./gradlew -v
```

### Install Web3j CLI

```bash
curl -L get.web3j.io | sh
. "$HOME/.web3j/source.sh"
web3j -V
```

### Install `solc` on macOS

```bash
brew update
brew upgrade
brew tap ethereum/ethereum
brew install solidity
solc --version
```

### Install `solc` on Linux

```bash
sudo add-apt-repository ppa:ethereum/ethereum
sudo apt-get update
sudo apt-get install solc
solc --version
```

### Verify Docker Fallback

```bash
docker --version
docker info
docker run --rm ghcr.io/argotorg/solc:stable --version
```

## How It Works

1. `SolcLocator` checks whether local `solc` is available with `solc --version`.
2. If local `solc` is available, compilation uses it.
3. If local `solc` is missing, the tool checks Docker CLI and Docker daemon availability.
4. `SolidityCompilerService` compiles each `.sol` file with optimizer enabled.
5. ABI and BIN artifacts are normalized into dedicated build directories.
6. `WrapperGenerationService` validates ABI/BIN pairs.
7. Web3j CLI generates Java wrappers with:

```bash
web3j generate solidity -b <bin> -a <abi> -o <java-output-dir> -p com.example.soliditycompiler.generated
```

## CLI Commands

The Java entrypoint is `com.example.soliditycompiler.Main`.

Supported commands:

- `compile`
- `generate-wrappers`
- `build-all`

## Gradle Tasks

- `./gradlew runCompile`
- `./gradlew runGenerateWrappers`
- `./gradlew runBuildAll`
- `./gradlew test`

### Compile Solidity Only

```bash
./gradlew runCompile
```

### Generate Wrappers

`runGenerateWrappers` depends on `runCompile`, so it compiles contracts first.

```bash
./gradlew runGenerateWrappers
```

### Full Build

`runBuildAll` performs the full pipeline:

1. Compile Solidity
2. Generate ABI and BIN
3. Generate Web3j wrappers
4. Compile Java sources including generated wrappers

```bash
./gradlew runBuildAll
```

## Example Contract

The sample contract is located at:

- `src/main/solidity/HelloWorld.sol`

It includes:

- a `string` state variable
- a constructor that accepts an initial greeting
- a getter
- a setter

## Testing

Unit tests are designed to run without external infrastructure.

They do not require:

- real `solc`
- Docker
- Web3j CLI
- a blockchain node

Run tests with:

```bash
./gradlew test
```

The tests cover:

- local `solc` detection
- Docker fallback selection
- command construction
- timeout handling
- non-zero exit handling
- wrapper generation preconditions
- Solidity file discovery

## Troubleshooting

### `solc` Not Found

If `solc --version` fails, the tool automatically tries Docker mode.

Checks:

```bash
solc --version
docker --version
docker info
```

### Docker Daemon Not Running

If Docker is installed but not running, the tool stops early with a clear error instead of deleting existing generated artifacts.

Checks:

```bash
docker info
docker ps
```

### `web3j` Not Found

Wrapper generation requires the Web3j CLI on `PATH`.

Checks:

```bash
web3j -V
which web3j
```

### Docker Permission Issues

If Docker fallback fails because of permissions, verify that your user can access Docker.

Checks:

```bash
docker ps
docker run --rm ghcr.io/argotorg/solc:stable --version
```

On Linux, you may need Docker post-install permission setup. On macOS, make sure Docker Desktop is fully started.

### Linux File Ownership Issues

When Docker fallback is used on POSIX systems, the tool passes the current UID and GID to `docker run` when available. This helps avoid root-owned generated files on Linux bind mounts.

### Import Path Issues

Compilation uses an explicit working directory and these `solc` options:

- `--base-path .`
- `--include-path src/main/solidity`

If you add more contracts or subdirectories, keep imports relative to `src/main/solidity` or relative to the importing file.

## Publishing to a New GitHub Repository

If you want to push this project to a new GitHub repository:

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin git@github.com:<your-account>/<your-repo>.git
git push -u origin main
```

If the repository already exists locally and you only need to connect and push:

```bash
git add .
git commit -m "Prepare project for GitHub"
git branch -M main
git remote add origin git@github.com:<your-account>/<your-repo>.git
git push -u origin main
```

For HTTPS instead of SSH:

```bash
git remote add origin https://github.com/<your-account>/<your-repo>.git
git push -u origin main
```

## Why This Design

- Java orchestrates all external tools directly
- `ProcessBuilder` is used consistently for execution
- commands always run with an explicit working directory
- stdout, stderr, exit code, command, and working directory are captured
- timeouts fail fast with a rich exception
- Docker fallback is automatic but validated carefully
- generated sources are compiled as part of the Gradle build
- the implementation avoids hidden build magic and avoids the Web3j Gradle plugin

## Reference Documentation

- [Web3j CLI](https://docs.web3j.io/4.8.7/command_line_tools/)
- [Solidity Installation](https://docs.soliditylang.org/en/latest/installing-solidity.html)
- [Docker Installation](https://docs.docker.com/get-started/introduction/get-docker-desktop/)

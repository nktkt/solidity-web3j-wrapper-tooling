// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

contract HelloWorld {
    string private greeting;

    constructor(string memory initialGreeting) {
        greeting = initialGreeting;
    }

    function getGreeting() external view returns (string memory) {
        return greeting;
    }

    function setGreeting(string calldata newGreeting) external {
        greeting = newGreeting;
    }
}


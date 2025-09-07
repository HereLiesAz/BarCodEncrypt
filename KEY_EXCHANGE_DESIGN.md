# Barcodencrypt v3 Cryptographic Design: Temporary Key Exchange

This document outlines a protocol for temporary key exchange, building upon the v2 rolling key system. It adapts concepts from the Double Ratchet algorithm to provide strong forward and post-compromise security.

## 1. Goals

-   **Forward Secrecy:** An attacker who compromises a user's keys cannot decrypt past messages.
-   **Post-Compromise Security:** If an attacker steals a user's keys, the user can "heal" the session and recover security after exchanging some messages.
-   **Asynchronous Operation:** The protocol must work in a one-way, asynchronous environment.

## 2. Core Concepts

This design uses a **Double Ratchet**, combining a **Diffie-Hellman (DH) ratchet** for key agreement with a **symmetric-key ratchet** for per-message keys.

-   **Barcode as Pre-Shared Key:** The v2 `IKM` (the barcode value) acts as the initial shared secret `SK` to bootstrap the protocol.
-   **DH Ratchet:** This is the "slow" ratchet, which moves forward when a party receives a message with a new DH public key. It uses the output of a DH calculation to re-seed the symmetric ratchet. This provides healing and post-compromise security.
-   **Symmetric-Key Ratchet:** This is the "fast" ratchet, which moves forward with every message. It uses the HKDF-based counter system from v2.

## 3. State Management

Each party (e.g., Alice) must maintain the following state for each contact (e.g., Bob) they communicate with:

-   `DHs`: Alice's own current DH ratchet key pair.
-   `DHr`: Bob's last known DH ratchet public key.
-   `RK`: A 32-byte Root Key for the root KDF chain.
-   `CKs`, `CKr`: 32-byte Chain Keys for Alice's sending and receiving symmetric-key chains.
-   `Ns`, `Nr`: Message numbers (counters) for the current sending and receiving chains.
-   `PN`: The number of messages in the previous sending chain, used to handle out-of-order messages.
-   `MKSKIPPED`: A dictionary to store message keys for out-of-order messages.

## 4. Protocol Flow

### 4.1. Initial Setup

1.  **Alice adds Bob:** Alice scans a barcode from Bob. This barcode's value is their initial shared secret `SK`.
2.  **Bob's Ratchet Key:** For this protocol to work, Alice must also know Bob's initial DH ratchet public key. Bob must generate a long-term DH key pair and provide the public key to Alice, perhaps via a second QR code. Alice stores this as `DHr`.
3.  **Alice's Initialization:** Alice generates her own first DH key pair (`DHs`). She then initializes her state using `SK` and her DH calculation with Bob's key, as per the Signal specification. She now has a root key (`RK`) and a sending chain key (`CKs`).

### 4.2. Alice Encrypts the First Message for Bob

1.  Alice performs a symmetric-key ratchet step on `CKs` to get a new `CKs` and a message key `MK`.
2.  She encrypts the plaintext with `MK`.
3.  She creates a `v3` header containing her current DH public key (`DHs.pub`), her previous chain length (`PN`), and her current message number (`Ns`).
4.  The final message is constructed and sent.

**Proposed v3 Header:** `BCE::v3::{opts}::{id}::{b64(DHs.pub)}::{PN}::{Ns}::{b64(payload)}`

### 4.3. Bob Decrypts Alice's First Message

1.  Bob receives the message and parses the `v3` header. He sees Alice's public key, `DHs.pub`.
2.  Since this is the first message, he knows he needs to perform a DH ratchet step.
3.  He performs a DH calculation with his private key and Alice's public key. The output is used to seed his root key `RK` and derive his first receiving chain key `CKr`, which will match Alice's sending chain.
4.  He then immediately performs a *second* DH ratchet step: he generates a *new* DH key pair for himself, derives a new `RK`, and a new sending chain `CKs`. This is the "ping-pong" of the DH ratchet.
5.  With the `CKr` he derived in step 3, he can now perform a symmetric-key ratchet step to get the message key `MK` and decrypt the message.
6.  Any subsequent messages Bob sends will contain his new DH public key.

### 4.4. Subsequent Exchanges

The parties continue this "ping-pong" exchange. When a party receives a message containing a new DH public key, they perform a DH ratchet step, updating their root key and deriving a new receiving and sending chain. When they receive a message with a public key they've already seen, they only perform a symmetric-key ratchet step on their receiving chain.

## 5. Security and Implementation Notes

-   **Elliptic Curve:** The `Curve25519` curve (specifically the `X25519` function) is recommended for the Diffie-Hellman calculations.
-   **State Storage:** The additional state (`DHs`, `DHr`, `RK`, etc.) must be stored securely per contact in the app's database. This requires a significant schema change.
-   **Complexity:** This protocol is significantly more complex than the v2 rolling key system. Implementation must be done with extreme care to avoid security flaws. The handling of out-of-order messages (`MKSKIPPED`) is particularly tricky.
-   **UX for Key Exchange:** The biggest user experience challenge is the initial exchange of Bob's long-term DH public key. This needs to be a smooth and understandable process for the user. A "Create Secure Channel" flow might be needed that guides the user to scan two QR codes: one for the shared secret and one for the contact's public key.

## 6. Implementation Blockers

As of the time of writing, the implementation of this v3 protocol is **blocked**.

The core of the Diffie-Hellman ratchet requires Elliptic Curve Cryptography, specifically the **X25519** function as recommended by the Signal protocol. The standard Java Cryptography Architecture (JCA) included in Android does not have a built-in provider for this curve.

Implementing this protocol correctly and securely requires a third-party cryptographic library, such as:
-   **BouncyCastle:** A widely-used, full-featured crypto provider.
-   **Google's Tink:** A high-level crypto library that simplifies many cryptographic tasks.

Since the current development environment does not allow for the addition of new dependencies, it is not possible to implement this protocol safely. Any attempt to implement the complex mathematics of ECC from scratch would be insecure and is strongly discouraged.

Therefore, the implementation of the v3 key exchange protocol is deferred until the dependency constraints are lifted.

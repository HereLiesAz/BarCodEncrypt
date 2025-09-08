# Barcodencrypt v2 Cryptographic Design

This document outlines the design for a new cryptographic model for Barcodencrypt, intended to introduce forward secrecy and improve the overall security posture of the application.

## 1. Goals

-   **Forward Secrecy:** If a single message key is compromised, it should not compromise the master key or any other message keys (past or future).
-   **Key Separation:** Keys used for different messages should be cryptographically separate, even though they derive from the same master secret.
-   **Deterministic Key Generation:** Both the sender and receiver must be able to generate the exact same message key given the same inputs.

## 2. Core Components

The new model is based on the **HMAC-based Key Derivation Function (HKDF)**, as specified in [RFC 5869](https://tools.ietf.org/html/rfc5869).

-   **Initial Keying Material (IKM):** The raw secret value of the physical barcode. This is the root secret.
-   **Salt:** A cryptographically random value **generated for each message**. The salt is not stored, but is prepended to the ciphertext. This ensures that even if the same message is encrypted twice with the same key and counter, the resulting ciphertext will be different.
-   **Pseudorandom Key (PRK):** A fixed-length, cryptographically strong key derived from the IKM and the per-message Salt using the **HKDF-Extract** step.
-   **Message Counter:** A simple integer, stored per-barcode, that is incremented for every encrypted message. This counter is the primary input that changes for each new key, enabling the "rolling" nature of the keys.
-   **Info String:** A context-specific byte string used in the HKDF-Expand step to ensure that keys generated for different purposes are unique. For example, `"BCEv2|msg"`.

## 3. Key Generation and Management

### 3.1. Adding a New Barcode

1.  The user scans a barcode, providing the **IKM**.
2.  The application stores the `(barcode_identifier, IKM)` tuple in the database.
3.  A **Message Counter** for this barcode is initialized to `0` in the database.

### 3.2. Encrypting a Message

1.  The user selects a recipient and a key (barcode).
2.  The application retrieves the `IKM` for the selected barcode.
3.  The **Message Counter** for that barcode is incremented in the database. Let the new value be `C`.
4.  The application generates a new random **Salt**.
5.  The application computes the **PRK** using HKDF-Extract: `PRK = HKDF-Extract(Salt, IKM)`.
6.  The application computes the unique message key using HKDF-Expand:
    `MessageKey = HKDF-Expand(PRK, info="BCEv2|msg" | C, length=32)`
7.  This `MessageKey` is used to encrypt the plaintext with AES-256-GCM.
8.  The `Salt` is prepended to the IV and ciphertext, and the result is Base64 encoded.
9.  The value of the counter `C` is embedded into the new v2 message header.

### 3.3. Decrypting a Message

1.  The application detects a v2 message and parses the header to extract the `barcode_identifier` and the `Message Counter` (`C`).
2.  The application retrieves the `IKM` for the given `barcode_identifier`.
3.  The application Base64-decodes the payload and extracts the **Salt** from the beginning of the payload.
4.  The application re-computes the **PRK** using the extracted Salt and the stored IKM: `PRK = HKDF-Extract(Salt, IKM)`.
5.  The application re-computes the message key using the exact same HKDF-Expand function as the sender:
    `MessageKey = HKDF-Expand(PRK, info="BCEv2|msg" | C, length=32)`
6.  This `MessageKey` is used to decrypt the rest of the payload (IV + ciphertext) with AES-256-GCM.

## 4. New Message Format (v2)

The message header will be updated to version 2 to include the counter.

**Proposed Format:** `BCE::v2::{options}::{barcode_identifier}::{counter}::{base64_payload}`

-   `v2`: Identifies the new cryptographic model.
-   `{counter}`: The message counter value used to generate the key.

## 5. Security Considerations

-   **Replay Attacks:** To prevent an attacker from re-sending an old, intercepted message, the decrypting client must also store a counter for each key it has a relationship with (the `last_seen_counter`). The client must reject any message where the incoming counter is less than or equal to its stored `last_seen_counter`. After a successful decryption, the client updates its `last_seen_counter` to the value from the message.
-   **Passphrase Security:** The entire system's security relies on the secrecy of the PRKs stored in the database. For hardening, the database itself should be encrypted at rest using a library like SQLCipher, with a key derived from a user-provided master password via the Android Keystore.
-   **Counter Synchronization:** If a user restores their data from a backup, their local counter may be out of sync with the sender's. A mechanism to manually reset a key's counter on both the sender's and receiver's devices might be necessary as an advanced feature.

## 6. Password Assistant Feature

The "Password Assistant" feature, which helps users fill password fields, operates outside of the cryptographic model described above.

-   When a user scans a barcode to fill a password, the **raw, un-hashed, un-encrypted value** of the barcode is used directly as the password.
-   This feature does not use the HKDF or any of the key derivation mechanisms. It is a simple utility to paste the content of a barcode into a text field.
-   The security of this feature is therefore dependent on the security of the barcode itself and the context in which it is used.

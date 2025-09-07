# BarCodEncrypt

*  I had this dream where, in the middle of our conversation, Dwayne "The Rock" Johnson pulled out his keys and scanned the barcode on a coupon club key fob.    
*  "What was that?" I asked.    
*  "Oh," The Rock showed me his fob. "This is my password."    
  
Barcodencrypt is a functional Android application for sending and receiving encrypted messages. The gimmick, the central conceit, is that the decryption key is a physical barcode. You scan it, you see the message. If you don't have the barcode, the message remains gibberish. It's security through deliberate inconvenience.

## Core Principles

1.  **Physical Keys:** Digital keys are ephemeral, easily copied. A physical barcode, while not Fort Knox, introduces a tangible barrier to entry.
2.  **Ephemeral Messages:** The sender dictates the lifespan of a message. It can be a fleeting whisper, visible only once (`single-use`), or a more persistent statement with a time-to-live (`ttl`).
3.  **Passive Detection:** The app, once granted the necessary permissions, watches passively. It doesn't live inside a specific messaging app; it lives on top of the screen, looking for its own encrypted spoor.

## How It Works

### Encryption
1.  **Manage Keys:** From the main screen, select "Manage Contact Keys". This allows you to pick a contact from your phone's address book.
2.  **Assign Barcode:** In the contact detail screen, tap the '+' button to open the scanner. Scan a barcode to assign it to that contact. You must give the barcode a unique identifier (e.g., "John's Work QR Code").
3.  **Compose:** From the main screen, select "Compose Message". Select your recipient and the specific key you want to use for them.
4.  **Encrypt & Share:** Write your message, choose your options (single-use or timed), and tap "Encrypt". The encrypted text can then be copied to the clipboard and pasted into any other application.

### Decryption
1.  **Enable Service:** From the main screen, enable the "Watcher Service" and grant the necessary Accessibility and Overlay permissions.
2.  **Detect:** When an encrypted message appears on screen (e.g., in a text message or email), the Watcher service will detect it and place a semi-transparent yellow overlay on it.
3.  **Scan:** Tap the overlay. The barcode scanner will open.
4.  **Reveal:** Scan the correct barcode that was used to encrypt the message. The overlay will turn green and reveal the plaintext. If the key is incorrect, it will turn red. The message will disappear according to the options it was encrypted with.

## Current Status

The application is feature-complete and demonstrates the core concept effectively. The UI is built with Jetpack Compose and Material 3. The encryption uses AES/GCM. While the core loop is complete, future work could include more advanced key exchange mechanisms, UI polish, and more robust error handling.

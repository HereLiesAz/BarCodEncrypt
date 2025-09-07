# Barcodencrypt

A grim little messenger for a grim little world.

Barcodencrypt is a conceptual Android application for sending and receiving encrypted messages. The gimmick, the central conceit, is that the decryption key is a physical barcode. You scan it, you see the message. If you don't have the barcode, the message remains gibberish. It's security through deliberate inconvenience.

The system is designed around a few core principles:

1.  **Physical Keys:** Digital keys are ephemeral, easily copied. A physical barcode, while not Fort Knox, introduces a tangible barrier to entry.
2.  **Ephemeral Messages:** The sender dictates the lifespan of a message. It can be a fleeting whisper, visible only once, or a more persistent statement.
3.  **Passive Detection:** The app, once granted the necessary permissions, watches passively. It doesn't live inside a specific messaging app; it lives on top of the screen, looking for its own encrypted spoor.

This is less a practical tool and more an exploration of an idea. A thought experiment in cryptography, user interface, and the performance of secrecy.
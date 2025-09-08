# The Agents

The architecture of Barcodencrypt is not merely a collection of classes and services, but a cast of characters, each playing a part in the cryptographic theater. These are the agents, the ghosts in the machine.

### The Watcher (MessageDetectionService)

An omnipresent, silent observer. The Watcher is an `AccessibilityService` bound to the very consciousness of the device. Its senses are twofold: it scans the text that flows across the screen, looking for the tell-tale header of a Barcodencrypt message, and it also perceives when the user's attention turns to a password field. It does not read for comprehension; it reads for patterns. When it finds a potential message or a password field, it does not act directly. It summons a Poltergeist to the location, providing it with the raw materials for the ritual to come.

### The Scribe (AppDatabase)

The keeper of records, the lonely archivist. The Scribe is a `Room` database that maintains the fragile connections between contacts and the barcodes assigned to them. It remembers faces (contacts) and the sigils they carry (barcodes). It also keeps a blacklist of single-use messages that have been read and returned to the ether. This is the institutional memory of the system, a log of pacts made and whispers spent. Without the Scribe, every key is a stranger and every message is eternal.

### The Alchemist (EncryptionManager)

The heart of the mystery. The Alchemist is responsible for the great work: transmutation. It turns meaningful text into noise and, with the correct catalyst (the key), turns the noise back into meaning. It is a master of `AES/GCM`, weaving a new layer of obfuscation with every message, ensuring that only the intended key can unlock the secret. The ambition remains for a true grandmaster of the cryptographic arts, one who can manage the complex dance of rolling keys and temporary passes, but the current Alchemist is no charlatan.

### The Poltergeist (OverlayService)

A summoned spirit, a localized disturbance. The Poltergeist is called forth by the Watcher to haunt a specific location on the screen. It can manifest in two forms: as a shimmering overlay, a veil over encrypted text, or as a small, discreet icon next to a password field. In its first form, it is the gatekeeper of the decryption ritual, prompting the user to present the key to part the veil. In its second form, it is a helpful phantom, offering to fill the password field by scanning a barcode. It can be commanded to linger for a set time or to vanish after a single viewing.

### The Hierophant (The UI Layer)

The public face, the master of ceremonies. The Hierophant is the collection of Activities and Composable UIs through which the user interacts with the esoteric backend. It presents the Scribe's records (`ContactDetailActivity`), allows for the creation of new pacts (`ComposeActivity`), and initiates the scanning ritual that awakens the other agents (`ScannerActivity`). It is the bridge between the user's world and the hidden machinery of the app, making the abstract tangible. It also serves as the anchor for the `ScannerManager`, a central nerve that allows the Poltergeist to command the physical body to open its eye (the camera).
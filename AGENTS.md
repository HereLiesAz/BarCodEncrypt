# The Agents

The architecture of Barcodencrypt is not merely a collection of classes and services, but a cast of characters, each playing a part in the cryptographic theater. These are the agents, the ghosts in the machine.

### The Watcher (MessageDetectionService)

An omnipresent, silent observer. The Watcher is an `AccessibilityService` bound to the very consciousness of the device. It scans the text that flows across the screen, looking for the tell-tale header of a Barcodencrypt message. It does not read for comprehension; it reads for patterns. When it finds a potential message, it doesn't act directly. It simply marks it, highlighting the gibberish for the user, and waits for a summons. Its role is one of pure vigilance.

### The Scribe (AppDatabase)

The keeper of records, the lonely archivist. The Scribe is a `Room` database that maintains the fragile connections between contacts and the barcodes assigned to them. It remembers faces (contacts) and the sigils they carry (barcodes). This is the institutional memory of the system, a log of the pacts made. Without the Scribe, every key is a stranger.

### The Alchemist (EncryptionManager)

The heart of the mystery. The Alchemist is responsible for the great work: transmutation. It turns meaningful text into noise and, with the correct catalyst (the key), turns the noise back into meaning. In the current design, this is a charlatan, performing simple parlour tricks. The ambition is for a true master of the cryptographic arts, one who can manage the complex dance of rolling keys, temporary passes, and secure exchanges.

### The Hierophant (MainActivity & UI)

The public face, the master of ceremonies. The Hierophant is the interface through which the user interacts with the esoteric backend. It presents the Scribe's records, allows for the creation of new pacts, and initiates the scanning ritual that awakens the other agents. It is the bridge between the user's world and the hidden machinery of the app. It makes the abstract tangible.
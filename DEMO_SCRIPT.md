# SonicCast — Live Hackathon Demonstration Script 🎯
### *"Offline Acoustic Broadcast — Detect. Verify. Recover. Discover. Sync."*

This step-by-step presentation script is tailored for the judging panel to verify **PS02: Acoustic One-to-Many Communication**, **Surprise Challenge 1 (Partial Reception)**, and **Surprise Challenge 2 (Dynamic Group)** in under 5 minutes.

---

## 📋 Pre-Demo Setup & Verification

1. **Devices Needed**:
   - **Device A (Sender)**: Organizer phone.
   - **Device B (Receiver 1)**: Student phone present during initial broadcast.
   - **Device C (Receiver 2 / Late Joiner)**: Student phone arriving late (Surprise Challenge 2).
2. **Network Isolation Proof (Crucial Judge Check)**:
   - On all devices, turn on **Airplane Mode**.
   - Ensure **Wi-Fi is OFF**, **Bluetooth is OFF**, and **Mobile Data is OFF**.
   - Show the Judges the app's `AndroidManifest.xml` or App Info permissions in Settings:
     `Permissions Granted: Microphone (Record Audio) ONLY`.
     Zero internet permission, zero network state permission, zero Bluetooth permission.

---

## 🚀 Phase 1: PS02 Core Acoustic Broadcast (1-to-Many)

### Script / Action:
1. **Launch SonicCast** on **Device A (Sender)** and select the **Sender (Transmitter)** tab.
2. **Launch SonicCast** on **Device B (Receiver 1)** and select the **Receiver** tab.
3. On **Device B**, observe the **Noise Calibration Banner**:
   - Status: `CALIBRATING ACOUSTIC NOISE (1.0s)...`
   - Within 1 second, it transitions to `AMBIENT NOISE CALIBRATED (Threshold: XX dB)`.
   - The **Acoustic Radar** begins pulsing passively in response to room acoustics.
4. On **Device A (Sender)**:
   - Select the preset button `"Exam Hall Announcement"` (or type `"Room 302: Quiz Key 7892"`).
   - Press **START ACOUSTIC BROADCAST**.
5. **Audible & Visual Observation**:
   - Device A’s speaker plays the Bell 202 CPFSK audio chirps (1200 Hz Space, 2200 Hz Mark).
   - On Device A, the transmission progress displays packet slices (`P0`, `P1`, `P2`, `P_PARITY`).
   - On Device B, the **Packet Visualizer** instantly turns green block-by-block as each packet's CRC-8 is verified.
   - Upon completion, Device B displays:
     `MESSAGE RECEIVED & VERIFIED ✓`
     `"Room 302: Quiz Key 7892"`
     `CRC Integrity: 100% Valid | Channel: Bell 202 FSK`

---

## 🛡 Phase 2: Surprise Challenge 1 — Partial Reception & Recovery

### Objective:
Demonstrate that when a receiver misses packets due to physical acoustic noise or obstruction:
1. The missing packet is detected via CRC-8 and packet index tracking.
2. The packet is **reconstructed locally using XOR Parity FEC without any retransmission**.
3. If multiple packets are lost, a selective acoustic NACK retransmits *only* the missing packet.

### Script / Action:
1. On **Device B (Receiver)**:
   - Turn ON the toggle: **"Simulate Partial Packet Loss (Drop Packet #1)"**.
   - Press **RESET / CLEAR**.
2. On **Device A (Sender)**:
   - Press **BROADCAST**.
3. **Observation during transmission**:
   - Device B receives `P0` (turns **Green ✓**).
   - Device B simulates physical obstruction on `P1` (turns **Red ✗ — Missing**).
   - Device B receives `P2` (turns **Green ✓**).
   - Device B receives `P_PARITY` (turns **Purple / Blue**).
4. **Instant XOR Parity Reconstruction**:
   - Notice the status immediately changes:
     `PARITY FEC RECOVERY TRIGGERED`
     `Missing Packet #1 reconstructed via XOR Parity Arithmetic!`
   - Packet `#1` in the grid shifts from Red to **Amber/Orange (Recovered)**.
   - Full message is reconstituted and verified with **zero retransmission delay**:
     `"Room 302: Quiz Key 7892"`
5. **Multi-Packet Loss (Tier 2 Selective NACK)**:
   - If packets `#0` and `#1` are lost (beyond single-packet parity capability):
   - Device B emits a compact acoustic NACK burst.
   - Device A logs: `NACK Detected for Packet #1. Retransmitting packet #1 only.`
   - Device A retransmits only packet `#1`. Full message completes.

---

## 👥 Phase 3: Surprise Challenge 2 — Dynamic Group (Late-Joining Receiver)

### Objective:
Demonstrate that a new phone entering the room **after** the broadcast has completed automatically receives the latest message **without the sender user manually pressing resend or restarting**.

### Script / Action:
1. **Device A (Sender)** has finished its broadcast.
   - Device A's screen shows the **Dynamic Group Card**:
     - Status: `GROUP ACTIVE`
     - Version: `#V1`
     - Announcement Mode: `● Broadcasting Beacon (Every 5s)`
     - *Point out to Judges: The sender phone is sitting untouched on the table!*
2. **Device C (Receiver 2 / Late Joiner)**:
   - Bring Device C into range and open SonicCast.
   - Switch to the **Receiver** tab.
   - Note: Device C was NOT present during Phase 1. Its local store has **NO message**.
3. **Autonomous Acoustic Sync Flow**:
   - Step 1: Device A emits its periodic acoustic `ANNOUNCEMENT` beacon frame (takes < 0.2s).
   - Step 2: Device C’s microphone captures the beacon and decodes `ANNOUNCEMENT [MsgId=1, Ver=1, Total=3]`.
   - Step 3: Device C checks its local `LatestMessageStore`: `hasMessage(ver=1) == false`.
   - Step 4: Device C's screen updates:
     `LATE JOINER DETECTED -> EMITTING ACOUSTIC SYNC REQUEST...`
   - Step 5: Device C emits a brief acoustic `SYNC_REQUEST` chirp.
   - Step 6: Device A’s microphone detects the sync request and logs:
     `Acoustic Sync Request received for Version #V1! Automatically retransmitting message packets...`
   - Step 7: Device A plays the message packets.
   - Step 8: Device C receives all packets, validates each individual CRC-8, and displays:
     `LATEST MESSAGE SYNCED ✓`
     `Text: "Room 302: Quiz Key 7892"`
     `Received: 3/3 Packets | CRC: VERIFIED | Version: #V1`
4. **Summary for Judges**:
   - The organizer **never touched** the sender phone.
   - The new receiver **automatically discovered** the group, identified the missing version, requested the sync acoustically, and verified data integrity.

---

## 💻 Phase 4: Automated In-Silico DSP Simulator (In-App Judge Mode)

If ambient room noise in the venue is extreme or the judges want to inspect the mathematical DSP algorithms:
1. Navigate to the **Debug / Diagnostic** screen in SonicCast.
2. Tap **"RUN FULL IN-SILICO ACOUSTIC SIMULATION"**.
3. Within 150 milliseconds, the test harness executes:
   - Text -> Binary Packet Framing -> 16-bit Preamble -> 0xD5 Marker -> CRC-8.
   - Bell 202 CPFSK Audio Synthesis (44.1 kHz PCM).
   - Dual Goertzel 1200/2200 Hz tone detection & symbol phase clock alignment.
   - Challenge 1: Single packet drop -> XOR Parity FEC mathematical reconstruction.
   - Challenge 2: Offline `LatestMessageStore` -> Announcement frame -> Sync request -> Auto retransmit.
4. All diagnostics report **PASSED (100% Accuracy, 0 Errors)**.

---

## 🏆 Scoring Checklist for Judges

- [x] **PS02 Acoustic Broadcast**: Complete 1-to-many FSK sound transmission.
- [x] **Zero Network Guarantee**: 100% functional in Airplane mode. Only `RECORD_AUDIO` permission.
- [x] **Surprise Challenge 1**: CRC-8 per packet, XOR Parity FEC local recovery, selective acoustic NACK.
- [x] **Surprise Challenge 2**: Dynamic late-joiner discovery, periodic acoustic beacon, acoustic sync request, zero sender manual intervention.
- [x] **Code Quality & Reliability**: 16/16 unit tests passing, production debug APK assembled.

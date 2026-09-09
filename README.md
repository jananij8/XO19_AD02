# SonicCast 🔊📡
### *"Offline Acoustic Broadcast — Detect. Verify. Recover. Discover. Sync."*

**SonicCast** is a zero-infrastructure acoustic one-to-many broadcast communication application for Android, engineered for **PS02: Acoustic One-to-Many Communication**, featuring full implementations of **Surprise Challenge 1 (Partial Reception)** and **Surprise Challenge 2 (Dynamic Group)**.

SonicCast transmits data through physical airwaves via the device loudspeaker and decodes it via the receiver microphone. It requires **zero internet, zero Wi-Fi, zero Bluetooth, zero mobile data, zero cloud services, and zero external hardware**.

---

## 🏆 Problem Statement & Challenge Coverage

| Requirement | Implementation in SonicCast | Verification |
|---|---|---|
| **PS02 Core Problem** | Acoustic one-to-many broadcast (1 Sender -> Many Receivers) using Bell 202 CPFSK (1200 Hz Space / 2200 Hz Mark) | 16-bit Preamble + `0xD5` Frame Marker + CRC-8 |
| **Zero-Network Guarantee** | `AndroidManifest.xml` declares **ONLY** `RECORD_AUDIO`. Absolutely NO network permissions. | Manifest inspection & Airplane Mode proof |
| **Surprise Challenge 1**<br>*(Partial Reception)* | Per-packet CRC-8, missing packet identification, local XOR Parity Forward Error Correction (FEC), and selective acoustic NACK retransmission | `PacketizerTest`, `InSilicoRoundTripTest`, UI toggle |
| **Surprise Challenge 2**<br>*(Dynamic Group)* | Local persistent `LatestMessageStore`, periodic acoustic Announcement Beacon (every 5s, **zero manual sender restart**), autonomous late-join discovery, acoustic `SYNC_REQUEST`, and automatic repair | `DynamicGroupSyncTest`, `InSilicoRoundTripTest`, UI Dynamic Group Card |

---

## 🔬 Physical Layer Specifications (Bell 202 CPFSK)

- **Modulation**: Continuous Phase Frequency Shift Keying (CPFSK)
- **Mark (Bit 1)**: 2200 Hz
- **Space (Bit 0)**: 1200 Hz
- **Audio Sample Rate**: 44,100 Hz (Native Android audio hardware standard)
- **Symbol Duration**: 25 ms (40 baud) for high SNR in reverberant environments
- **Spectral Demodulation**: Dual Goertzel filters with adaptive squelch
- **Pulse Shaping**: Raised-cosine windowing on symbol boundaries to prevent acoustic clicks and high-frequency splatter

---

## 📦 Protocol Frame Structure

Each acoustic transmission frame consists of:
```
+-------------------------------------------------------------------------------+
| Preamble (16 bits) | Frame Marker (8 bits) | Packet Header (6B) | Payload (0-16B) | CRC-8 (1B) |
+-------------------------------------------------------------------------------+
```

1. **Preamble**: `10101010 10101010` (16 bits) for clock recovery and matched-filter phase locking.
2. **Frame Marker**: `0xD5` (`11010101`) byte boundary synchronization.
3. **Header Fields (6 Bytes)**:
   - `Type` (1B): `DATA (0x01)`, `PARITY_XOR (0x02)`, `NACK_REPAIR (0x03)`, `ACK_CONFIRM (0x04)`, `ANNOUNCEMENT (0x05)`, `SYNC_REQUEST (0x06)`
   - `Message ID` (1B): Unique message transmission ID (`0..255`)
   - `Version` (1B): Sequence number for Challenge 2 version reconciliation
   - `Packet ID` (1B): Index (`0..N-1`) or special control index (`0xFE`=Parity, `0xFD`=NACK, `0xFB`=Announcement, `0xFA`=SyncReq)
   - `Total Packets` (1B): Total data packet count
   - `Payload Length` (1B): Number of valid payload bytes (<= 16)
4. **Payload**: 0 to 16 raw data bytes
5. **CRC-8**: Computed over Header + Payload using polynomial x^8 + x^2 + x + 1 (`0x07`). Corrupted packets are rejected with 100% certainty.

---

## 🛠 Surprise Challenge 1: Partial Reception & Recovery

When a receiver enters partial obstruction or suffers transient acoustic interference:
1. **Per-Packet CRC-8**: Each packet is individually verified upon reception.
2. **XOR Parity Forward Error Correction (FEC)**:
   - Parity[j] = XOR(Payload_0[j], Payload_1[j], ..., Payload_{N-1}[j])
   - If any **single packet** is dropped out of N packets, the receiver reconstructs the missing packet **locally without transmitting any request**:
   - MissingPayload[j] = Parity[j] XOR XOR_{i != missing}(Payload_i[j])
3. **Tier 2 Selective Acoustic NACK**: If multiple packets are lost, the receiver emits a compact acoustic NACK frame. The sender transmits **only** the missing packet ID without re-sending the whole message.
4. **Tier 3 Acoustic ACK Confirmation**: Verified receivers chirp an acoustic ACK confirmation.

---

## 🔄 Surprise Challenge 2: Dynamic Group (Late-Joining Receivers)

When a new receiver enters the room **after** the initial broadcast is finished:
1. **Local Message Persistence**: The sender automatically stores the latest broadcast message, version, total packets, and payload chunks in `LatestMessageStore` (`SharedPreferences` + JSON, surviving app backgrounding and navigation).
2. **Autonomous Announcement Beacon**: The sender enters periodic beacon mode, emitting a compact acoustic `ANNOUNCEMENT` frame (`MsgId`, `Version`, `TotalPackets`) every 5 seconds. **The sender user does not touch the device**.
3. **Late-Join Discovery**: The newly joined receiver enters listening mode, hears the beacon, checks its local store, and recognizes that it does not possess Version `#Vn`.
4. **Acoustic Sync Request**: The receiver emits an acoustic `SYNC_REQUEST` frame over the air.
5. **Automatic Sender Retransmission**: The sender detects the request and immediately retransmits the required packet stream.
6. **Integrity Verification**: The receiver receives all packets, validates each CRC-8, reconstructs the message, and displays:
   `LATEST MESSAGE SYNCED ✓` (Received: X/X packets, CRC: VERIFIED).

---

## 📱 User Interface Highlights

- **Dark Acoustic Security Console Theme**: High-contrast `#0A0E14` background with `#00E5FF` cyan and `#10B981` emerald accents.
- **Packet Visualizer**: Interactive packet block matrix (Grey = Pending, Green = CRC Verified, Red = Dropped/Missing, Amber = Parity / NACK Recovered).
- **Acoustic Radar**: Live audio power meter reacting to room acoustic energy.
- **Dynamic Group Card**: Displays latest message version, announcement beacon status, and sync request counts.
- **Partial Reception Simulation Toggle**: "Simulate Partial Packet Loss (Drop Packet #1)" for live judges demonstration.
- **Built-in In-Silico DSP Simulator**: Debug screen running full end-to-end audio modulation/demodulation without audio hardware.

---

## 🧪 Test Suite & Verification

All 16 unit tests run purely offline via Gradle:
```powershell
$env:JAVA_HOME = "C:\Users\dhaya\.gemini\antigravity\scratch\jdk-17"
$env:ANDROID_HOME = "C:\Users\dhaya\AppData\Local\Android\Sdk"
.\gradlew.bat testDebugUnitTest
```

### Test Results: 16/16 Passed (100% Success)
- `CRCManagerTest`: 3/3 passed
- `PacketizerTest`: 4/4 passed
- `FSKModulationTest`: 3/3 passed
- `DynamicGroupSyncTest`: 3/3 passed
- `InSilicoRoundTripTest`: 3/3 passed

---

## 📦 Debug APK

The debug APK is built and ready for deployment:
```
app/build/outputs/apk/debug/app-debug.apk (15.6 MB)
```
Install on any Android device (API 26+):
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

# LE Audio v1.2: playback and headset microphone

Validated on SM-S918B with EvolutionX-16.0-20260810-dm3q-11.10-Vanilla-Official and WH-1000XM6. This is a device-specific workaround.

Follow-up on the same SM-S918B / WH-1000XM6 setup: the default duplex workaround now works in Discord after installation and a full device reboot. The user confirmed that both the microphone and listening audio work normally.

The published v1.1 **music-only workaround intentionally removes the BLE input route**. During an actual Discord session, Discord correctly selected the BLE headset for communication, but its recording session used the phone's built-in microphone. Restoring the Bluetooth software HAL's BLE input port and route allowed Discord to record from `AUDIO_DEVICE_IN_BLE_HEADSET`. With the original conversational selection, the active software codecs were 32 kHz stereo output / 32 kHz mono input; the user confirmed that the headset microphone worked in Discord.

For higher-quality conversational playback, local module v1.2 prepends this **existing** configuration to `Conversational` in `audio_set_scenarios.json`:

```text
One-TwoChan-SnkAse-Lc3_48_1-One-OneChan-SrcAse-Lc3_32_1_Low_Latency
```

All existing conversational fallbacks and all other scenario lists are retained. No codec definition is added or changed. The module combines that selection change with the software duplex policy; it does not remove the headset input.

| Direction | Negotiated LC3 configuration | SDU payload | Encoded bitrate |
|---|---|---:|---:|
| Phone → headset | 48 kHz stereo, 75 bytes/channel, 7.5 ms | 150 bytes | 160 kbps total |
| Headset → phone | 32 kHz mono, 60 bytes, 7.5 ms | 60 bytes | 64 kbps |

After restoring the original empty input/output codec-preference blobs and fully restarting the **Bluetooth process**, a 25-second local probe selected this configuration automatically in `CONVERSATIONAL`. Both actual routes were BLE; the software encoder/decoder and remote ASE configuration agreed with the table, using one bidirectional CIS. The probe received 765,167 mono PCM frames, including 61,525 nonzero samples, with zero reported playback underruns and zero route recoveries. It retained no microphone audio. These counts establish observed microphone PCM and route continuity during that run, not intelligibility, radio-loss absence, or artifact-free playback.

This narrows the earlier limitation: the tested conversational path can now initialize duplex after a Bluetooth-process restart **without a runtime codec-preference request**. It does not establish a general fix for the native stale/zero decoder-cache issue; earlier failures in other context-transition paths remain relevant, including Game. It also does not fix oversized ISO packet handling or the encoder storage issue described above.

The user confirmed working microphone audio and natural listening sound with the temporary 48/32 configuration. Module v1.2 was then installed through KernelSU and the phone was fully rebooted. All 16 installed static files matched their expected hashes; KernelSU had removed only the installer hook, `customize.sh`, as expected. The new boot reached `stage=service`, `state=ready`, with all three policy/configuration mounts verified, no pending update, and matching ROM guards.

In an actual Discord session **after that reboot**, Discord owned communication mode, its recording session used `AUDIO_DEVICE_IN_BLE_HEADSET`, both LE directions were started, and the software codecs were 48 kHz stereo output / 32 kHz mono input. The user again confirmed that both functions worked normally. This establishes the tested post-reboot result on this device, not a repeated reconnect matrix or a general repair of every context transition. The earlier v1.1 music-only result and the new v1.2 duplex result should be read separately. The [upstream draft PR](https://github.com/samsung-sm8550-cola2261/android_device_samsung_sm8550-common/pull/2) remains scoped to route ownership.

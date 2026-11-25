# Brief: LifeCue Alarm Clock & Reminder (com.riniso.lifecue)

**Speaks:** English quotes and reminder texts, often when an alarm fires.
**Do first:** it is the simplest live-speech case, and it tests the
alarm-time fallback.

## What QVoice brings

Natural offline English voices, male and female, at alarm time, with no
network. Google's offline voices are fine, but QVoice's built-in and Piper
voices are a clear step up and start just as fast.

## Changes

1. Add the `<queries>` entry and copy RinisoSpeech (guide steps 1–2).
2. In the alarm/reminder service, replace its TextToSpeech with
   **one** RinisoSpeech built with `USAGE_ALARM` audio attributes, so it
   follows the alarm volume and rings through Do Not Disturb the way the
   alarm sound does.
3. When the alarm fires: call `speech.warmUp(locale)` first, then build the
   text, then speak with `fastStart = true` and `startTimeoutMs = 3000`.
   An alarm must never wait silently for a slow voice. After 3 s the phone's
   engine says it instead.
4. Keep the alarm's own ringtone/vibration logic exactly as it is. Speech is
   added on top: if both fail, the alarm still rings.
5. Settings: the one voice choice in Me › Voice (Automatic / Your phone's
   voice / A voice you choose), a voice per alarm in the editor's Advanced
   section, the card with `source = "lifecue"`, and "Hear a sample" saying
   who spoke. This is LifeCue's own plan (26 Nov), adopted for all apps.
6. Cues users write in any language: the guide's "Text in any language".
7. Later (LifeCue's Chunk B, BL-035): render the next ring's words to a WAV
   file in device-protected storage, ahead of time, and play that at ring
   time. It works before the first unlock after a restart, and lets
   QVoice's slow, best voices wake the user.

## Watch out

- If speech runs in a foreground service of type
  alarm/`systemExempted`/`mediaPlayback`, keep that type. Don't add a new
  FGS type for this.
- Test from a locked phone, screen off, with QVoice's process killed
  (`adb shell am force-stop com.riniso.qvoice`): the first alarm must still
  speak within 3 s.

## Tests (on the phone)

1. QVoice not installed: alarm speaks with the phone's voice, as before.
2. QVoice installed, built-in voice: alarm speaks with QVoice, also after a
   force-stop of QVoice.
3. In QVoice, make Kokoro the default English voice. The alarm still
   speaks at once (fastStart picks a fast voice).
4. Setting "Phone's default voice": QVoice is never used.

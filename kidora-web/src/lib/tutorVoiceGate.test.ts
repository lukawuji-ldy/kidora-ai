import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  AUDIO_TAIL_MS,
  AUTO_LISTEN_AFTER_TTS_MS,
  PlayGeneration,
  estimateMinSpeakMs,
  postEndedHoldMs,
  shouldArmAutoListen,
} from "./tutorVoiceGate.ts";

describe("tutorVoiceGate", () => {
  it("stop invalidates in-flight play so ended/error must not arm listen", () => {
    const gen = new PlayGeneration();
    const token = gen.begin();
    gen.stop();
    assert.equal(gen.isLive(token), false);
  });

  it("natural end stays live until stop, then only current token is live", () => {
    const gen = new PlayGeneration();
    const first = gen.begin();
    assert.equal(gen.isLive(first), true);
    const second = gen.begin();
    assert.equal(gen.isLive(first), false);
    assert.equal(gen.isLive(second), true);
  });

  it("does not arm auto-listen while tutor playback is still live", () => {
    assert.equal(
      shouldArmAutoListen({
        autoListen: true,
        readOnly: false,
        recording: false,
        playbackLive: true,
      }),
      false,
    );
  });

  it("arms auto-listen only after playback finished and idle mic", () => {
    assert.equal(
      shouldArmAutoListen({
        autoListen: true,
        readOnly: false,
        recording: false,
        playbackLive: false,
      }),
      true,
    );
  });

  it("keeps a longer post-TTS gap so last sentence is not clipped by mic open", () => {
    assert.ok(AUTO_LISTEN_AFTER_TTS_MS >= 1000);
  });

  it("estimates min speak time from caption length so short audio cannot arm early", () => {
    const short = estimateMinSpeakMs("Hi!");
    const long = estimateMinSpeakMs(
      "Hi! I'm Lily. Good morning! Look, a cat and a dog! Which one do you like?",
    );
    assert.ok(long > short);
    assert.ok(long >= 5000);
  });

  it("holds after early ended until estimated speak time elapses", () => {
    const text =
      "Hi! I'm Lily. Good morning! Look, a cat and a dog! Which one do you like?";
    const min = estimateMinSpeakMs(text);
    const hold = postEndedHoldMs(2000, text);
    assert.ok(hold >= min - 2000);
    assert.ok(hold >= AUTO_LISTEN_AFTER_TTS_MS);
  });

  it("uses only post-roll when playback already covered estimated speak time", () => {
    const text = "Nice!";
    const min = estimateMinSpeakMs(text);
    const hold = postEndedHoldMs(min + 500, text);
    assert.equal(hold, AUTO_LISTEN_AFTER_TTS_MS);
  });

  it("keeps an audio tail after ended so last phoneme is not clipped by cleanup", () => {
    assert.ok(AUDIO_TAIL_MS >= 400);
  });
});

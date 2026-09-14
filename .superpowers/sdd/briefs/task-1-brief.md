### Task 1: TTS 鍗曟祴涓庢敞閲婂彛寰勶紙涓嫳姝ｆ枃 + 鍓ユ嫭鍙凤級

**Files:**
- Modify: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java`
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java`锛堜粎 Javadoc锛岄€昏緫涓嶅彉闄ら潪鍗曟祴鏆撮湶缂哄彛锛?
- Test: 鍚屼笂 Test 绫?

**Interfaces:**
- Consumes: `CetLessonService.speakableForTts(String)`锛沗buildSpeechExtras(...)`
- Produces: 鍗曟祴閿佸畾銆屼腑鏂囪剼鎵嬫灦 + 鑻辨枃 + 鎷彿娉ㄩ噴銆嶇殑 TTS 琛屼负

- [ ] **Step 1: 鎵╁睍澶辫触/鍏堝啓鏂█锛圱DD锛?*

鍦?`speakableForTts_stripsChineseParentheticalHints` 鏈熬杩藉姞锛?

```java
assertEquals(
        "璇村緱涓嶉敊锛佷綘鍙互璇?\"My dog is white\"銆俉hat color is your dog?",
        CetLessonService.speakableForTts(
                "璇村緱涓嶉敊锛佷綘鍙互璇?\"My dog is white\"銆俉hat color is your dog? (浣犵殑鐙楁槸浠€涔堥鑹诧紵)"));
```

灏?`buildSpeechExtras_ttsUsesEnglishOnly` **閲嶅懡鍚?*涓?`buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish`锛屽苟鎶?stub 鏈熸湜鏀逛负娣峰悎鍙ワ細

```java
@Test
void buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish() {
    SpeechToolPort port = new SpeechToolPort() {
        @Override
        public Optional<AsrResult> asr(String audioBase64, String locale) {
            return Optional.empty();
        }

        @Override
        public Optional<TtsResult> tts(String text, String voice, String locale) {
            assertEquals(
                    "璇村緱涓嶉敊锛佷綘鍙互璇?\"My dog is white\"銆俉hat color is your dog?",
                    text);
            return Optional.of(new TtsResult("xx", "audio/wav", "stub"));
        }

        @Override
        public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
            return Optional.empty();
        }
    };
    List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
            port,
            "璇村緱涓嶉敊锛佷綘鍙互璇?\"My dog is white\"銆俉hat color is your dog? (浣犵殑鐙楁槸浠€涔堥鑹诧紵)",
            null, "en-US", null, null);
    assertEquals(1, extras.size());
}
```

淇濈暀鍘熸湁鑻辨枃+鎷彿鐢ㄤ緥锛坄Do you have a pet? (浣犳湁瀹犵墿鍚楋紵)`锛変綔涓哄洖褰掋€?

- [ ] **Step 2: 璺戞祴璇曠‘璁ょ幇鐘?*

Run锛堝湪 `cet-tutor-core` 鐩綍锛?

```powershell
mvn -q -Dtest=CetLessonServiceTest#speakableForTts_stripsChineseParentheticalHints,CetLessonServiceTest#buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish test
```

Expected: **PASS**锛堢幇鏈?`speakableForTts` 宸插墺鎷彿骞朵繚鐣欎腑鑻憋級銆傝嫢 FAIL锛岃繘鍏?Step 3 淇鍒欙紱鑻?PASS锛孲tep 3 鍙敼娉ㄩ噴銆?

- [ ] **Step 3: 鏇存柊 Javadoc锛堥€昏緫閫氬父鏃犻渶鏀癸級**

`buildSpeechExtras` 娉ㄩ噴鏀逛负锛?

```java
/**
 * 鏋勫缓 TTS / 鍙戦煶闄勫姞浜嬩欢銆?
 * TTS 鏈楄姘旀场姝ｆ枃锛堜腑鏂囪剼鎵嬫灦 + 鑻辨枃渚嬪彞锛夛紝鍘绘帀鍚腑鏂囩殑鎷彿娉ㄩ噴锛涙皵娉″師鏂囦粛瀹屾暣涓嬪彂銆?
 *
 * @param voice 浜鸿鏄犲皠闊宠壊锛沶ull 琛ㄧず涓嶄紶 voice锛堝巶鍟嗛粯璁わ級
 */
```

`speakableForTts` 娉ㄩ噴鏀逛负锛?

```java
/**
 * 渚?TTS 鏈楄锛氬幓鎺夊惈涓枃鐨勬嫭鍙锋敞閲婏紝淇濈暀涓枃涓诲彞涓庤嫳鏂囦緥鍙ャ€?
 * 渚嬪 {@code What color is your dog? (浣犵殑鐙楁槸浠€涔堥鑹诧紵)} 鈫?{@code What color is your dog?}锛?
 * {@code 璇村緱涓嶉敊锛丮y dog is white銆倉 淇濇寔涓嶅彉銆?
 * 鍙戦煶璇勬祴浠嶇敱璋冪敤鏂逛紶鍏ョ嫭绔?{@code referenceText}锛堥€氬父涓鸿嫳鏂囩洰鏍囧彞锛夛紝鏈柟娉曚粎瀵瑰叾鍋氬悓鏍峰墺鎷彿銆?
 *
 * @param text 澶栨暀姘旀场鍏ㄦ枃鎴栧弬鑰冨彞
 * @return 閫傚悎鏈楄鐨勬鏂囷紱鑻ュ墺绂诲悗涓虹┖鍒欏洖閫€鍘熸枃
 * @author liudy
 */
```

鑻?Step 2 FAIL锛氭鏌ユ鍒?`[锛?][^锛?]*[\\u4e00-\\u9fff][^锛?]*[锛?]` 鏄惁璇激寮曞彿鍐呭唴瀹癸紱浠ャ€屽彧鍒犲惈姹夊瓧鐨勬嫭鍙锋銆嶄负鍘熷垯寰皟锛屽嬁鍒犱腑鏂囨鏂囥€?

- [ ] **Step 4: 鍐嶈窇鏁寸被娴嬭瘯**

```powershell
mvn -q -Dtest=CetLessonServiceTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```powershell
git add cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java
git commit -m "test(cet): lock TTS speakable text as Chinese+English minus parentheticals"
```

---

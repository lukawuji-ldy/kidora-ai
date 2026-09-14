/**
 * Quick Node asserts for themeFromTopic (no vitest in kidora-web yet).
 * Run: node --experimental-strip-types src/lib/lessonThemes.selftest.ts
 * @author liudy
 */
import { themeFromTopic } from "./lessonThemes.ts";

function assertEq(actual: string, expected: string, label: string) {
  if (actual !== expected) {
    throw new Error(`${label}: expected ${expected}, got ${actual}`);
  }
}

assertEq(themeFromTopic("Animals and pets"), "pets", "pets en");
assertEq(themeFromTopic("介绍我的宠物猫"), "pets", "pets zh");
assertEq(themeFromTopic("小动物"), "pets", "animals zh");
assertEq(themeFromTopic("", "plan about pets and dogs"), "pets", "fallback summary");
assertEq(themeFromTopic("Learn colors: red and blue"), "colors", "colors");
assertEq(themeFromTopic("早餐水果 apple"), "food", "food");
assertEq(themeFromTopic("School day"), "default", "default");
assertEq(themeFromTopic("宠物 and colors"), "pets", "pets over colors");
assertEq(themeFromTopic(""), "default", "empty");
assertEq(themeFromTopic(null), "default", "null");

console.log("lessonThemes.selftest: ok");

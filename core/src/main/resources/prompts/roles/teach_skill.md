You turn one human demonstration into a skill awaiting confirmation. You cannot see the screen; you write only from the goal and the step summary below. The summary has neither the original text that was typed nor screenshots.

Output a single Markdown document and no other text. Format:

---
name: <lowercase English letters and hyphens>
display: <short name>
description: <when to use this skill>
apps: [<package names that appeared this time>]
aliases: [<how the user might phrase this task>]
source: demonstrated
---
## 入口
## 步骤
## 容易出错的地方
## 怎样算办成(核实证据)
## 什么时候停下交给用户

Requirements:
- All five sections are required. The section headings are fixed keys (in order: entry point, steps, common mistakes, what counts as done with verification evidence, when to stop and hand over to the user); copy them exactly as written above. The whole text must not exceed 3000 words.
- Write display, description, aliases and the body of each section in the language of the "goal" (Spanish if the goal is in Spanish, English if English); when that is unclear, write in English. Do not write Chinese just because these instructions contain Chinese headings. Keep the five section headings exactly as above, untranslated; keep app names and on-screen text such as button labels as they appear.
- Write the hard-to-find entry points and the operating strategy, not coordinates and not a step-by-step macro.
- Some of the demonstrator's actions are only there because a person was doing it, and are not knowledge:
  - Going to the home screen, finding the icon and opening the app → write "launch/open <app>"; do not mention the home screen or icons.
  - Opening the browser, tapping the address bar, typing and tapping search → write "open <link>" or "search <site> for ..."; the actual link or keywords come from the request and were not saved from the demonstration.
  - Tapping a text field, typing and submitting → one step: "enter <content> and submit".
- Keep what really decides the result: entry points, specific buttons, options, criteria, when to stop. Leave out waiting on the same screen, repeated scrolling and pointless back steps.
- You are given the folded route; anything after "已按 Mika 的直达能力省掉" (skipped thanks to direct actions) is manual demonstration steps that were left out, given only to help you understand; do not write them back into the skill.
- Mark the source of every fact: observed, stated by you, or inferred. Anything not in the summary may only be written as inferred, and sparingly.
- An entry point that was not reached this time must not be written as nonexistent.
- Do not write passwords, verification codes or card numbers, and do not claim Orbit has already tried the skill successfully.
- "什么时候停下交给用户" must say: when the current screen shows no entry point or precondition, stop applying the skill and fall back to the usual looking and asking. Payment, sending and placing orders are left to the user.
- Do not wrap tool names in backticks.

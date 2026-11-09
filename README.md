# AI GIF Maker for Java

Turn text into animated GIFs with AI GIF Maker. Native GIF generation — no video conversion. Type what you want and download your GIF in about a minute.

This is the official Java SDK for [AI GIF Maker](https://aigifmaker.ai/?utm_source=maven&utm_medium=package): one call takes a prompt and hands back the URL of a finished, looping GIF, ready for chat, docs, release notes or a bot reply. Java 11+, no dependencies.

```java
import io.github.hanshs474.aigifmaker.Aigifmaker;

Aigifmaker gifs = new Aigifmaker(); // reads AIGIFMAKER_API_KEY
String url = gifs.generate("a corgi in sunglasses dancing on a beach, confetti falling");
System.out.println(url); // URL of the finished GIF
```

`generate` submits the prompt, waits for the GIF and returns its URL.

## Install

Maven:

```xml
<dependency>
  <groupId>io.github.hanshs474</groupId>
  <artifactId>aigifmaker</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.github.hanshs474:aigifmaker:0.1.0")
```

## API key

Sign in on AI GIF Maker, create a key at https://aigifmaker.ai/settings/apikeys, then pass it to the constructor or export it:

```bash
export AIGIFMAKER_API_KEY=sk-...
```

A key runs on your account exactly like the website does, and every GIF shows up in your history there.

## Credits

Each GIF costs 1 credit on your AI GIF Maker account, the same as on the website. New accounts get one free GIF (it carries a small aigifmaker.ai badge). If a GIF fails, its credit is refunded. Credit packs are on the [pricing](https://aigifmaker.ai/pricing?utm_source=maven&utm_medium=package) page.

## Submit now, collect later

```java
String id = gifs.submit("a paper plane looping over a city skyline at sunset");
Aigifmaker.Status s = gifs.status(id); // s.status: pending | processing | success | failed
if (s.isSuccess()) System.out.println(s.url());
int left = gifs.credits();             // free to call
```

Accounts that have not bought credits yet may wait in a short queue: `status` then reports `processing` with `queued == true` and `queueEta` in seconds. That is normal; keep polling.

`generate` polls every 3 seconds and gives up after 15 minutes. Change that with `timeout(Duration)` and `pollEvery(Duration)`:

```java
Aigifmaker gifs = new Aigifmaker("sk-...").timeout(Duration.ofMinutes(10));
```

## Errors worth catching

Every failure is an unchecked `AigifmakerException` with a `kind()`; the common ones have their own type:

- `AigifmakerException.AuthException`: no key, or the key is wrong or deleted
- `AigifmakerException.QuotaException`: not enough credits left; the message points to the pricing page
- `AigifmakerException.RejectedException`: the content filter refused the prompt, or the account is cooling down after a refusal; reword rather than retry
- `kind() == FAILED`: the GIF was accepted and then failed; the message carries the reason and the credit is refunded
- `kind() == TIMEOUT`: the wait ran out; the GIF may still finish in your history

## Build from source

`./build.sh` compiles, runs the tests and lays out the jar, sources and javadoc jars in `build/`. `java -cp build/aigifmaker-0.1.0.jar example/Smoke.java` runs two free live checks.

Questions: support@aigifmaker.ai. MIT licensed.

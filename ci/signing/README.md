# Public test signing key

`test-keystore.p12.b64` is a dedicated **public test fixture**, not a production key.
Both PR APKs use it, so an update can keep local drafts in each test app.

- Format: PKCS12, RSA 2048.
- Alias: `pole-parkla-test`.
- Store and key passwords: `android`.
- SHA-256 certificate: `E1:18:C6:B7:42:21:42:E1:7A:14:DF:DF:C9:74:24:FE:16:55:67:D6:8B:86:45:16:4B:D9:F8:0E:8F:23:07:B7`.

Anyone can sign an APK with this key. Use these apps only for testing code you trust.
Never use this key for a production release. Do not replace it unless you accept
that existing Debug and Preview installs will need to be removed first.

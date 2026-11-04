# images/

Nothing here yet. A debug build exists (`dist/csapp-1.0.0-debug.apk`), so screenshots
can be taken once the app has run on a phone (`docs/VERIFICATION.md`).

Still needed:

- `icon.png` 512x512, no alpha. The launcher icon is only an adaptive-icon XML
  (`res/mipmap-anydpi-v26/ic_launcher.xml`), so the 512 px PNG has to be rendered from
  its vector foreground. Keep the hexagon inside the 66 dp safe zone.
- `featureGraphic.png` 1024x500, no alpha
- `phoneScreenshots/` an incident cluster, the triage view, the confirm-before-send
  dialog, and the AI suggestion with its On-device or Cloud badge

**Fabricate or anonymise every piece of review text.** Publishing real users'
complaints, usernames or avatars in a store listing is a privacy problem regardless
of the reviews being public on Play, and it makes your own users' criticism part of
your marketing without their say-so. The same goes for email: never screenshot a real
mailbox.

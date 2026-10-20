# Connected R018 HUD failure review

The owner connected with R018 and supplied `connected-r018-hud.png`. The screenshot
shows the XP, custom Health/Mana/Stamina, and custom ability textures as red missing-
texture placeholders. It also shows the XP element occupying the left half of the
screen instead of being centered over the hotbar.

The corresponding client log is:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Logs\2026-09-07_08-27-00_client.log
SHA-256 BFA2F7B0181E7D2E5DE6359DB2550DEC29993BF216C6AD103D1359E22A5CFEEE
```

The screenshot retained with this review has SHA-256
`2EF55C200A4AF1F2BCB4E8729F5E682F45C64FD002E08C3E4ED1DBFE892730F1`.

The log proves the mod assets were successfully transferred and cached under logical
paths such as:

```text
UI/Custom/Assets/RpgHud/ExperienceBackground.png
UI/Custom/Assets/RpgHud/ExperienceBar.png
UI/Custom/Assets/RpgHud/ExperienceFrame.png
UI/Custom/RpgHud.ui
```

R018 requested `Common/UI/Custom/Assets/RpgHud/...` from inside the document. The
client resource namespace strips the leading `Common/`, so those absolute-looking
requests did not resolve. R019 uses paths relative to `RpgHud.ui`, for example
`Assets/RpgHud/ExperienceFrame.png`.

The left-side placement was independent of the texture failure. R018 used
`Horizontal: 0` together with a fixed width. In this anchor grammar, `Horizontal`
sets both horizontal margins and stretches the element; it is not a center-position
value. R019 centers the fixed-width element by omitting Left, Right, and Horizontal,
matching the installed native Health/Stamina HUD documents.

No server exception, world disconnect, or gameplay-state failure was found in the
reviewed session. This is connected failure evidence for R018 presentation only.


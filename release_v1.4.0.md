## v1.4.0

### Added
- **Duck module** (Render): solid duck orbit visual effect.
- **Arraylist continuous frame**: every module row is boxed with a 3px-radius card and rows sit flush together, forming one continuous frame column (Hengshui-era style).
- **ClickGUI sub-option alignment**: Rise font renderer now uses strict top-left glyph semantics (vanilla-consistent), so property labels align exactly with their capsules, chips and slider tracks.
- **MyauInjector process scan**: WMI-based Minecraft process discovery (with PEB fallback) so the injector reliably finds the game process.

### Remake
- **KillAura**: merged NewKillAura into KillAura — 12 autoblock modes (FULL / ONYX / BLOCKHIT), rotation modes (NORMAL / NEAREST / SMART), fullTick / onyxTrigger / blockHitTime / blockHitDelay settings.
- **HUD**: rewritten to Onyx StyleModule spec — 14px rows, 2px continuous accent bar, slide-fade + scale entry animation, frosted-glass cards.
- **NoSlow**: added ONYX mode.
- **ClickGUI headers**: title / search-hint / panel-title vertical centering now uses real rendered font height instead of estimated baseline math.

### Remove
- **BlockHit** — merged into KillAura autoblock modes.
- **NewKillAura** — merged into KillAura.
- **ModuleToggleNotify** — notification toasts removed.

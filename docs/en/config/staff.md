# Staff & CoreProtect

Both feature groups are **off** by default and are meant for server staff (helpers
and above).

## Staff features (/warn)

Adds a warning button right in PMs and the global chat.

| Key | Default | Description |
|---|---|---|
| `staffFeatures` | `false` | Enable the `/warn` button |
| `warnCommand` | `"warn"` | The warn command (without a slash) |
| `warnReason` | `""` | Default reason (empty — don't add one) |

## CoreProtect

A separate tab for CoreProtect plugin output. Lines are shown as-is, without
translation.

| Key | Default | Description |
|---|---|---|
| `coreProtectEnabled` | `false` | Enable the CoreProtect log tab |
| `coreProtectPattern` | regex | Pattern to capture the plugin's lines |

::: warning Not for everyone
These features only help those with the matching permissions on the server.
Regular players have no reason to enable them.
:::

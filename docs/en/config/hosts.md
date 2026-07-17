# File hosts

Photos, stickers, GIFs and voice notes upload to public file hosts. The retry
order is set in `uploadOrder` — codes separated by commas, leftmost first.

| Code | Host | Note |
|---|---|---|
| `k` | kappa.lol | first by default |
| `x` | x0.at | |
| `q` | qu.ax | |
| `c` | catbox.moe | blocked in Russia — listed last |

Default value:

```json
"uploadOrder": "k,x,q,c"
```

## Checking availability

The `/pm hosts` command iterates over all hosts and shows which respond from your
connection. If photos don't upload, start there.

::: tip Reorder them
If, say, only `x0.at` is stable for you, put it first:
`"uploadOrder": "x,k,q,c"`.
:::

## URL overrides

In `hostOverrides` you can manually override the download URL per host code — in
case domains or mirrors change.

| Key | Description |
|---|---|
| `uploadUrl` | Upload URL (catbox API by default) |
| `imageHost` | Base URL for downloading by id |
| `hostOverrides` | A "host code → URL" map for overrides |

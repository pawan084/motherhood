# backend/media — own-hosted video assets

The Videos feature streams the product's **own** short-form videos from this
directory, mounted read-only at `/media` by `app.py` (only when this directory
exists). `StaticFiles` honours HTTP Range headers, so the in-app player can seek.

**The media files are not in git** — they are large binaries and are gitignored
(`.gitignore` un-ignores *only* this README). A fresh clone has the catalog rows
(seeded by `seed_videos.py`) but no files, so `/media/...` returns 404 until you
populate the directory. That is the honest empty state; nothing pretends a video
is playable when the file is absent.

## Layout

`seed_videos.py` points each catalog row at these relative paths (clients prefix
their API base URL):

```
backend/media/
└── videos/
    ├── portrait/     <name>.mp4   ← streamed on phones (the default)
    ├── landscape/    <name>.mp4   ← web/tablet variant, beside the portrait one
    └── thumbs/       <name>.png   ← thumbnail per video
```

The six seeded `<name>`s (one file each in `portrait/`, `landscape/`, `thumbs/`):

| id | stage | name |
|---|---|---|
| `own-benefit`  | pregnant           | `benefit`  |
| `own-dodont`   | pregnant           | `dodont`   |
| `own-listicle` | pregnant           | `listicle` |
| `own-question` | postpartum         | `question` |
| `own-myths`    | postpartum         | `myths`    |
| `own-stat`     | trying to conceive | `stat`     |

## Populate it

Drop the produced files into the paths above (e.g.
`backend/media/videos/portrait/benefit.mp4`). Restart the backend so the `/media`
mount is created, then a catalog row reports its video as playable. To add a new
video, add a row in `seed_videos.py` (seed-once, so it only affects fresh
databases — or add it via the admin surface) and place the matching files here.

> HUMAN-GATED (TODO.md): like all seeded content, the videos need content review
> before real users see them.

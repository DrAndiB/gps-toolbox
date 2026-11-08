#!/usr/bin/env python3
"""Summarise GPS Toolbox telemetry as Markdown.

Usage:
    python tools/telemetry_report.py <checkout of the telemetry repo> [--days N]

Reads every events/**/*.jsonl file. Each file starts with a {"meta": 1, ...} device line,
followed by one JSON event per line: {"t": epoch_ms, "e": name, "s": session, ...}.
"""
import argparse
import datetime as dt
import json
import statistics as st
from collections import Counter, defaultdict
from pathlib import Path

SCREEN_CAP_MS = 30 * 60 * 1000  # ignore gaps longer than this when summing screen time
QUIET = {"gnss", "screen", "app_open", "app_close", "gnss_start", "gnss_stop"}


def load(root: Path, days: int | None):
    events, devices = [], {}
    cutoff = None
    if days:
        cutoff = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=days)).timestamp() * 1000
    for path in sorted(root.rglob("*.jsonl")):
        meta = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            try:
                o = json.loads(line)
            except json.JSONDecodeError:
                continue
            if o.get("meta"):
                meta = o
                devices[o.get("device", "?")] = o
                continue
            if cutoff and o.get("t", 0) < cutoff:
                continue
            o["_device"] = meta.get("device", "?")
            o["_app"] = meta.get("app", "?")
            events.append(o)
    events.sort(key=lambda o: o.get("t", 0))
    return events, devices


def when(ms):
    return dt.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")


def med(values, digits=1):
    xs = [v for v in values if isinstance(v, (int, float))]
    return round(st.median(xs), digits) if xs else "–"


def pct(part, whole):
    return f"{100 * part / whole:.0f} %" if whole else "–"


def screen_minutes(events):
    total = defaultdict(float)
    current, start, session = None, None, None
    for o in events:
        e, t = o["e"], o["t"]
        if o.get("s") != session:
            current, start, session = None, None, o.get("s")
        if e == "screen":
            if current and start:
                total[current] += min(t - start, SCREEN_CAP_MS)
            current, start = o.get("name"), t
        elif e == "app_open":
            current, start = o.get("screen") or current, t
        elif e == "app_close":
            if current and start:
                total[current] += min(t - start, SCREEN_CAP_MS)
            start = None
    return {k: v / 60000 for k, v in total.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", type=Path)
    ap.add_argument("--days", type=int, default=None)
    args = ap.parse_args()
    events, devices = load(args.root, args.days)
    if not events:
        print("No telemetry events found.")
        return

    by = defaultdict(list)
    for o in events:
        by[o["e"]].append(o)

    print(f"# GPS Toolbox telemetry\n")
    print(f"{len(events)} events, {when(events[0]['t'])} to {when(events[-1]['t'])}")
    for tag, m in devices.items():
        print(f"- Device `{tag}`: {m.get('manufacturer')} {m.get('model')}, Android API {m.get('android')}, "
              f"GNSS `{m.get('gnss_hw') or '?'}` ({m.get('gnss_year')}), sensors {', '.join(m.get('sensors', []))}")
    print(f"- App versions seen: {', '.join(sorted({o['_app'] for o in events}))}")

    opens, closes = by["app_open"], by["app_close"]
    fg_min = sum(o.get("fg_s", 0) for o in closes) / 60
    days_used = len({when(o["t"])[:10] for o in opens})
    print(f"\n## Usage\n")
    print(f"- {len(opens)} app opens on {days_used} days, {fg_min:.0f} min in the foreground "
          f"(median visit {med([o.get('fg_s') for o in closes], 0)} s)")
    print("\n| Screen | Minutes | Share |\n| --- | ---: | ---: |")
    mins = screen_minutes(events)
    total_min = sum(mins.values()) or 1
    for name, m in sorted(mins.items(), key=lambda kv: -kv[1]):
        print(f"| {name} | {m:.1f} | {100 * m / total_min:.0f} % |")

    actions = Counter(o["e"] for o in events if o["e"] not in QUIET)
    if actions:
        print("\n| Action | Count |\n| --- | ---: |")
        for name, n in actions.most_common():
            print(f"| {name} | {n} |")

    settings = by["setting"]
    if settings:
        print("\n## Settings changes\n")
        for o in settings:
            print(f"- {when(o['t'])}: {o.get('key')} → {o.get('value')}")

    snaps = by["gnss"]
    if snaps:
        fixed = [o for o in snaps if o.get("fix")]
        print("\n## GNSS quality (one snapshot per minute while running)\n")
        print(f"- {len(snaps)} snapshots, fix in {pct(len(fixed), len(snaps))}, "
              f"{pct(sum(1 for o in snaps if not o.get('fg')), len(snaps))} in the background")
        print(f"- Median horizontal accuracy {med([o.get('acc') for o in fixed])} m, vertical {med([o.get('vacc') for o in fixed])} m")
        print(f"- Median satellites used/tracked {med([o.get('used') for o in snaps], 0)}/{med([o.get('tracked') for o in snaps], 0)}, "
              f"dual-band in {pct(sum(1 for o in fixed if o.get('dual_band')), len(fixed))} of fixes")
        print(f"- Median HDOP {med([o.get('hdop') for o in fixed])}, top-4 L1 C/N0 {med([o.get('cn0_top4') for o in snaps])} dB-Hz, "
              f"update rate {med([o.get('rate_hz') for o in fixed])} Hz")
        stale = [o for o in fixed if (o.get("age_s") or 0) > 5]
        if stale:
            print(f"- {len(stale)} snapshots with a fix older than 5 s (possible stalls)")
        per_system = defaultdict(list)
        for o in snaps:
            for name, (used, tracked) in (o.get("systems") or {}).items():
                per_system[name].append((used, tracked))
        if per_system:
            print("\n| System | Median used | Median tracked |\n| --- | ---: | ---: |")
            for name, vals in sorted(per_system.items()):
                print(f"| {name} | {med([u for u, _ in vals], 0)} | {med([t for _, t in vals], 0)} |")

    fixes = by["first_fix"]
    if fixes:
        print("\n## Time to first fix\n\n| When | TTFF | A-GNSS age | Note |\n| --- | ---: | ---: | --- |")
        for o in fixes:
            age = o.get("agnss_age_h")
            note = "after cold start" if o.get("after_cold_start") else ("background" if not o.get("fg") else "")
            print(f"| {when(o['t'])} | {o.get('ttff_ms', 0) / 1000:.1f} s | {f'{age:.1f} h' if age is not None else 'never'} | {note} |")

    refresh = by["agnss_refresh"]
    if refresh:
        bg = [o for o in refresh if o.get("bg")]
        print("\n## Assistance data (A-GNSS)\n")
        print(f"- {len(refresh)} refreshes ({len(bg)} background, {len(refresh) - len(bg)} manual)")
        ok = [o for o in refresh if o.get("time") or o.get("psds") or o.get("xtra")]
        print(f"- Accepted by the phone: {pct(len(ok), len(refresh))} "
              f"(time {sum(1 for o in refresh if o.get('time'))}, PSDS {sum(1 for o in refresh if o.get('psds'))}, "
              f"XTRA {sum(1 for o in refresh if o.get('xtra'))}, skipped {sum(1 for o in refresh if o.get('skipped'))})")
        gaps = [(b["t"] - a["t"]) / 3_600_000 for a, b in zip(bg, bg[1:])]
        if gaps:
            print(f"- Background interval: median {med(gaps)} h, longest {max(gaps):.1f} h")

    problems = [o for o in events if o["e"] in ("crash", "gnss_error", "service_error")
                or (o["e"] == "raw_registration" and not o.get("ok"))]
    print("\n## Errors\n")
    if not problems:
        print("None recorded.")
    for o in problems:
        detail = o.get("error") or o.get("where") or ""
        print(f"- {when(o['t'])} `{o['e']}` {detail} (screen {o.get('screen', '?')}, app {o['_app']})")
        if o["e"] == "crash":
            print("\n```\n" + "\n".join(o.get("stack", "").splitlines()[:15]) + "\n```\n")


if __name__ == "__main__":
    main()

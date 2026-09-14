#!/usr/bin/env python3
"""Analyze samsung_standby_recorder.sh output without claiming unsampled behavior."""

from __future__ import annotations

import argparse
import csv
import json
import tempfile
from collections import Counter
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


FALSE_VALUES = {"false", "0", "no"}
DOZE_CONFIRMED_STATES = {"IDLE", "IDLE_MAINTENANCE"}


def read_env(path: Path) -> Dict[str, str]:
    values: Dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def integer(value: Any) -> Optional[int]:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return None


def number(value: Any) -> Optional[float]:
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return None


def read_samples(path: Path) -> Tuple[List[Dict[str, str]], List[str]]:
    warnings: List[str] = []
    rows: List[Dict[str, str]] = []
    if not path.exists():
        return rows, ["samples.tsv is missing"]
    with path.open("r", encoding="utf-8", errors="replace", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if not reader.fieldnames or "wall_epoch" not in reader.fieldnames:
            return rows, ["samples.tsv has no usable header"]
        for line_number, row in enumerate(reader, start=2):
            epoch = integer(row.get("wall_epoch"))
            if epoch is None:
                warnings.append(f"ignored malformed sample line {line_number}")
                continue
            clean = {key: (value or "") for key, value in row.items() if key is not None}
            clean["wall_epoch"] = str(epoch)
            rows.append(clean)
    rows.sort(key=lambda item: integer(item.get("wall_epoch")) or 0)
    return rows, warnings


def condition_valid(row: Dict[str, str]) -> bool:
    return (
        row.get("battery_ac", "").lower() in FALSE_VALUES
        and row.get("battery_usb", "").lower() in FALSE_VALUES
        and row.get("battery_wireless", "").lower() in FALSE_VALUES
        and row.get("power_is_powered", "").lower() in FALSE_VALUES
        and row.get("power_plug_type") == "0"
        and row.get("interactive", "").lower() in FALSE_VALUES
    )


def delta(first: Optional[int], last: Optional[int]) -> Optional[int]:
    if first is None or last is None:
        return None
    return last - first


def first_last_numeric(
    rows: Iterable[Dict[str, str]], field: str
) -> Tuple[Optional[int], Optional[int]]:
    values = [value for value in (integer(row.get(field)) for row in rows) if value is not None]
    return (values[0], values[-1]) if values else (None, None)


def artifact_info(directory: Path, name: str) -> Dict[str, Any]:
    path = directory / name
    return {
        "path": name,
        "present": path.is_file(),
        "bytes": path.stat().st_size if path.is_file() else 0,
    }


def analyze(directory: Path) -> Dict[str, Any]:
    meta = read_env(directory / "protocol.env")
    state = read_env(directory / "state.env")
    rows, warnings = read_samples(directory / "samples.tsv")

    start = integer(meta.get("protocol_start_epoch") or state.get("protocol_start_epoch"))
    deadline = integer(meta.get("deadline_epoch") or state.get("deadline_epoch"))
    target = integer(meta.get("target_duration_seconds") or state.get("target_duration_seconds"))
    interval = integer(meta.get("sample_interval_seconds") or state.get("sample_interval_seconds"))
    ended = integer(state.get("ended_wall_epoch"))
    phase = state.get("phase", "missing")
    reason = state.get("reason", "")

    if start is not None and deadline is None and target is not None:
        deadline = start + target
    if target is None and start is not None and deadline is not None:
        target = deadline - start

    in_window = [
        row
        for row in rows
        if (epoch := integer(row.get("wall_epoch"))) is not None
        and (start is None or epoch >= start)
        and (deadline is None or epoch <= deadline)
        and row.get("phase") == "running"
    ]
    valid_rows = [row for row in in_window if condition_valid(row)]
    violations = [row for row in in_window if not condition_valid(row)]

    boundary_points: List[Tuple[int, str]] = []
    if start is not None:
        boundary_points.append((start, "protocol_start"))
    boundary_points.extend(
        (integer(row["wall_epoch"]) or 0, "sample") for row in in_window
    )
    if deadline is not None and phase == "complete":
        boundary_points.append((deadline, "protocol_deadline"))
    boundary_points = sorted(set(boundary_points))
    gap_threshold = max((interval or 300) + 30, int((interval or 300) * 1.5))
    gaps: List[Dict[str, Any]] = []
    for (left, left_kind), (right, right_kind) in zip(boundary_points, boundary_points[1:]):
        duration = right - left
        if duration > gap_threshold:
            gaps.append(
                {
                    "start_epoch": left,
                    "end_epoch": right,
                    "duration_seconds": duration,
                    "between": f"{left_kind}->{right_kind}",
                }
            )

    sample_epochs = [integer(row.get("wall_epoch")) for row in valid_rows]
    sample_epochs = [value for value in sample_epochs if value is not None]
    confirmed_span = sample_epochs[-1] - sample_epochs[0] if len(sample_epochs) >= 2 else 0
    actual_end = ended or (integer(rows[-1].get("wall_epoch")) if rows else None)
    actual_wall = actual_end - start if actual_end is not None and start is not None else None
    nominal_window = (
        max(0, min(actual_end, deadline) - start)
        if actual_end is not None and start is not None and deadline is not None
        else None
    )

    level_first, level_last = first_last_numeric(in_window, "battery_level")
    counter_first, counter_last = first_last_numeric(in_window, "charge_counter_uah")
    temperatures = [
        value
        for value in (integer(row.get("temperature_tenths_c")) for row in in_window)
        if value is not None
    ]

    service_rows = [row for row in in_window if row.get("service_present") == "1"]
    service_absent_rows = [row for row in in_window if row.get("service_present") == "0"]
    service_unknown_rows = [
        row for row in in_window if row.get("service_present") not in {"0", "1"}
    ]
    service_absent = len(service_absent_rows)
    pids = [row.get("package_pid", "").strip() for row in service_rows]
    pids = [pid for pid in pids if pid]
    pid_transitions = sum(left != right for left, right in zip(pids, pids[1:]))
    refresh_counts = [
        value
        for value in (integer(row.get("refresh_count")) for row in service_rows)
        if value is not None
    ]
    refresh_resets = sum(left > right for left, right in zip(refresh_counts, refresh_counts[1:]))
    restart_evidence = max(pid_transitions, refresh_resets)

    comparisons: List[Dict[str, Any]] = []
    for row in service_rows:
        system_level = integer(row.get("battery_level"))
        service_level = integer(row.get("service_battery_level"))
        if system_level is not None and service_level is not None:
            comparisons.append(
                {
                    "wall_epoch": integer(row.get("wall_epoch")),
                    "system_level": system_level,
                    "service_level": service_level,
                    "difference": service_level - system_level,
                }
            )
    mismatch_count = sum(item["difference"] != 0 for item in comparisons)

    deep_states = Counter(
        row.get("deviceidle_deep", "unknown").strip().upper() or "UNKNOWN"
        for row in in_window
    )
    light_states = Counter(
        row.get("deviceidle_light", "unknown").strip().upper() or "UNKNOWN"
        for row in in_window
    )
    confirmed_doze = any(state_name in DOZE_CONFIRMED_STATES for state_name in deep_states)
    non_active_doze = any(state_name not in {"ACTIVE", "UNKNOWN", ""} for state_name in deep_states)

    error_rows = list(in_window)
    if rows and rows[-1].get("phase") == "post_deadline":
        error_rows.append(rows[-1])
    sample_errors = [
        {"wall_epoch": integer(row.get("wall_epoch")), "error": row.get("sample_errors")}
        for row in error_rows
        if row.get("sample_errors")
    ]

    endpoint = rows[-1] if rows else None
    endpoint_after_deadline = bool(
        endpoint
        and deadline is not None
        and (integer(endpoint.get("wall_epoch")) or 0) >= deadline
    )
    endpoint_compliant = condition_valid(endpoint) if endpoint_after_deadline and endpoint else None

    status = "incomplete"
    if phase == "interrupted":
        status = "interrupted"
    elif not meta:
        warnings.append("protocol.env is missing; the qualifying observation never started or metadata was lost")
    elif not in_window:
        warnings.append("no in-window samples; no result can be accepted")
    elif phase != "complete":
        warnings.append(f"recorder is not finalized (phase={phase}); rerun it with the same device directory")
    elif target is None or nominal_window is None or nominal_window < target:
        warnings.append("finalized data does not cover the requested wall-clock duration")
    elif violations:
        status = "interrupted"
        warnings.append("one or more in-window samples observed power or interactive-state violations")
    else:
        status = "observed"

    if sample_errors:
        warnings.append("one or more samples contain command timeout/read errors")
    if service_unknown_rows:
        warnings.append("one or more service observations are unknown; they are not counted as service absence")
    if gaps:
        warnings.append("sampling gaps exceed the expected cadence; deep sleep may explain them, but the gap is unsampled")
    if endpoint_compliant is False:
        warnings.append(
            "the first observation at/after the deadline was powered or interactive; "
            "the data does not establish when that condition changed"
        )
    if phase == "complete" and not endpoint_after_deadline:
        warnings.append("recorder finalized without an observation at or after the deadline")
    if status == "observed" and (
        gaps or sample_errors or service_unknown_rows or endpoint_compliant is not True
    ):
        status = "observed_with_gaps"

    evidence_quality = "none"
    if in_window:
        evidence_quality = (
            "limited"
            if gaps or sample_errors or service_unknown_rows or endpoint_compliant is not True
            else "regular_samples"
        )

    return {
        "format_version": 1,
        "status": status,
        "recorder_phase": phase,
        "recorder_reason": reason,
        "evidence_quality": evidence_quality,
        "protocol": {
            "start_epoch": start,
            "deadline_epoch": deadline,
            "target_duration_seconds": target,
            "actual_wall_duration_seconds": actual_wall,
            "nominal_observation_seconds": nominal_window,
            "observer_finish_delay_seconds": (
                max(0, actual_end - deadline)
                if actual_end is not None and deadline is not None
                else None
            ),
            "sample_interval_seconds": interval,
        },
        "conditions": {
            "in_window_sample_count": len(in_window),
            "valid_sample_count": len(valid_rows),
            "violation_count": len(violations),
            "violation_epochs": [integer(row.get("wall_epoch")) for row in violations],
            "first_valid_epoch": sample_epochs[0] if sample_epochs else None,
            "last_valid_epoch": sample_epochs[-1] if sample_epochs else None,
            "sampled_valid_span_seconds": confirmed_span,
            "discrete_samples_only": True,
            "post_deadline_endpoint_present": endpoint_after_deadline,
            "post_deadline_endpoint_compliant": endpoint_compliant,
        },
        "sampling_gaps": gaps,
        "battery": {
            "level_first_percent": level_first,
            "level_last_percent": level_last,
            "level_change_points": delta(level_first, level_last),
            "charge_counter_first_uah": counter_first,
            "charge_counter_last_uah": counter_last,
            "charge_counter_change_uah": delta(counter_first, counter_last),
            "temperature_min_tenths_c": min(temperatures) if temperatures else None,
            "temperature_max_tenths_c": max(temperatures) if temperatures else None,
            "attribution": "total device change only; not attributable to this app",
        },
        "service": {
            "present_sample_count": len(service_rows),
            "absent_sample_count": service_absent,
            "unknown_sample_count": len(service_unknown_rows),
            "all_in_window_samples_present": (
                bool(in_window) and service_absent == 0 and not service_unknown_rows
            ),
            "observed_pids": list(dict.fromkeys(pids)),
            "pid_transition_count": pid_transitions,
            "refresh_count_reset_count": refresh_resets,
            "restart_evidence_count": restart_evidence,
            "refresh_count_first": refresh_counts[0] if refresh_counts else None,
            "refresh_count_last": refresh_counts[-1] if refresh_counts else None,
            "refresh_count_change": (
                refresh_counts[-1] - refresh_counts[0] if refresh_counts else None
            ),
            "screen_off_timer_note": (
                "An unchanged refreshCount while noninteractive is expected; it does not measure "
                "WorkManager, manifest receivers, or direct repository refreshes."
            ),
        },
        "snapshot_consistency": {
            "comparable_sample_count": len(comparisons),
            "matching_sample_count": len(comparisons) - mismatch_count,
            "mismatch_count": mismatch_count,
            "comparisons": comparisons,
            "interpretation": (
                "A mismatch is a sampled stale service snapshot, not by itself a timer failure."
            ),
        },
        "doze": {
            "deep_state_counts": dict(deep_states),
            "light_state_counts": dict(light_states),
            "deep_idle_observed": confirmed_doze,
            "non_active_progression_observed": non_active_doze,
            "claim": (
                "deep IDLE/IDLE_MAINTENANCE observed"
                if confirmed_doze
                else "deep idle was not confirmed by the available samples"
            ),
        },
        "command_errors": sample_errors,
        "artifacts": {
            "batterystats_start": artifact_info(directory, "batterystats-start.txt"),
            "batterystats_end": artifact_info(directory, "batterystats-end.txt"),
            "scoped_logcat_start": artifact_info(directory, "logcat-start.txt"),
            "scoped_logcat_end": artifact_info(directory, "logcat-end.txt"),
        },
        "warnings": warnings,
    }


def value_or_unknown(value: Any, suffix: str = "") -> str:
    return "unknown" if value is None else f"{value}{suffix}"


def render_report(summary: Dict[str, Any], source: Path) -> str:
    protocol = summary["protocol"]
    conditions = summary["conditions"]
    battery = summary["battery"]
    service = summary["service"]
    consistency = summary["snapshot_consistency"]
    doze = summary["doze"]
    artifacts = summary["artifacts"]

    lines = [
        "# Samsung standby observation",
        "",
        f"Evidence status: **{summary['status']}** (recorder phase: `{summary['recorder_phase']}`).",
        "",
        "## Protocol evidence",
        "",
        f"- Requested wall time: {value_or_unknown(protocol['target_duration_seconds'], ' s')}",
        f"- Target-window wall time reached by the recorder: {value_or_unknown(protocol['nominal_observation_seconds'], ' s')}",
        f"- Recorder wall duration: {value_or_unknown(protocol['actual_wall_duration_seconds'], ' s')}",
        f"- Finish observation delay: {value_or_unknown(protocol['observer_finish_delay_seconds'], ' s')}",
        f"- Samples inside the target window: {conditions['in_window_sample_count']}",
        f"- Samples confirming unplugged and noninteractive: {conditions['valid_sample_count']}",
        f"- Observed condition violations: {conditions['violation_count']}",
        f"- Span between first and last confirming samples: {conditions['sampled_valid_span_seconds']} s",
        f"- Sampling gaps over cadence allowance: {len(summary['sampling_gaps'])}",
        f"- Post-deadline endpoint compliant: {value_or_unknown(conditions['post_deadline_endpoint_compliant'])}",
        "",
        "Samples are discrete observations. They do not prove that no brief screen-on or power event occurred between samples. A long gap can be consistent with deep sleep, but the gap itself is unobserved.",
        "",
        "## Battery and service",
        "",
        f"- System level: {value_or_unknown(battery['level_first_percent'], '%')} → {value_or_unknown(battery['level_last_percent'], '%')} (change {value_or_unknown(battery['level_change_points'], ' points')})",
        f"- Charge counter: {value_or_unknown(battery['charge_counter_first_uah'], ' µAh')} → {value_or_unknown(battery['charge_counter_last_uah'], ' µAh')} (change {value_or_unknown(battery['charge_counter_change_uah'], ' µAh')})",
        "- These changes describe total device discharge. They cannot be attributed to Battery Widget.",
        f"- Service present/absent/unknown samples: {service['present_sample_count']}/{service['absent_sample_count']}/{service['unknown_sample_count']}",
        f"- Observed service PIDs: {', '.join(service['observed_pids']) or 'none'}",
        f"- PID transitions: {service['pid_transition_count']}; refresh-count resets: {service['refresh_count_reset_count']}",
        f"- Service refreshCount: {value_or_unknown(service['refresh_count_first'])} → {value_or_unknown(service['refresh_count_last'])}",
        f"- Comparable service/system battery samples: {consistency['comparable_sample_count']}; mismatches: {consistency['mismatch_count']}",
        "- An unchanged service refreshCount while the screen is off is expected. It does not count WorkManager, manifest receiver, or direct repository refreshes.",
        "",
        "## Doze observations",
        "",
        f"- Deep states: `{json.dumps(doze['deep_state_counts'], sort_keys=True)}`",
        f"- Light states: `{json.dumps(doze['light_state_counts'], sort_keys=True)}`",
        f"- Evidence: {doze['claim']}.",
        "",
        "Natural Doze observations do not establish exact timer delivery or maintenance-window timing.",
        "",
        "## Scoped artifacts",
        "",
        f"- Start/end package batterystats: {artifacts['batterystats_start']['present']} / {artifacts['batterystats_end']['present']}",
        f"- Start/end fixed-tag logcat: {artifacts['scoped_logcat_start']['present']} / {artifacts['scoped_logcat_end']['present']}",
        "- Batterystats is cumulative since charge and is an estimate. No batterystats reset or checkin mutation was used.",
    ]
    if summary["sampling_gaps"]:
        lines.extend(["", "## Sampling gaps", ""])
        for gap in summary["sampling_gaps"]:
            lines.append(
                f"- {gap['start_epoch']} → {gap['end_epoch']}: {gap['duration_seconds']} s ({gap['between']})"
            )
    if summary["warnings"]:
        lines.extend(["", "## Warnings", ""])
        lines.extend(f"- {warning}" for warning in summary["warnings"])
    if summary["status"] == "incomplete":
        lines.append("")
        if summary["recorder_phase"] in {"running", "waiting"}:
            lines.append(
                "The observer did not finalize enough evidence. Preserve the directory and check its recorded PID first; only if that observer is dead should the recorder be restarted with the same directory."
            )
        else:
            lines.append(
                "The recorder finalized this directory without enough evidence. Preserve it for review and use a new independent directory for another run."
            )
    lines.extend(["", f"Source directory: `{source}`", ""])
    return "\n".join(lines)


def self_test() -> None:
    fields = [
        "wall_epoch", "wall_iso", "phase", "wall_elapsed_s", "gap_s", "uptime_s",
        "battery_ac", "battery_usb", "battery_wireless", "battery_status", "battery_level",
        "battery_scale", "charge_counter_uah", "temperature_tenths_c", "power_wakefulness",
        "power_is_powered", "power_plug_type", "stay_on_while_plugged", "interactive",
        "deviceidle_deep", "deviceidle_light", "package_pid", "process_start_ticks",
        "service_present", "monitoring_enabled", "monitoring_running",
        "service_screen_interactive", "refresh_count", "last_refresh_elapsed_ms",
        "service_battery_level", "last_start_error", "sample_errors",
    ]
    with tempfile.TemporaryDirectory(prefix="standby-analysis-") as raw:
        directory = Path(raw)
        (directory / "protocol.env").write_text(
            "protocol_start_epoch=1000\ndeadline_epoch=8200\n"
            "target_duration_seconds=7200\nsample_interval_seconds=300\n",
            encoding="utf-8",
        )
        (directory / "state.env").write_text(
            "phase=complete\nreason=deadline_observed\nended_wall_epoch=8300\n",
            encoding="utf-8",
        )
        base = {field: "" for field in fields}
        samples = []
        for epoch, level, counter, deep, pid, count, present in [
            (1000, 80, 3_000_000, "ACTIVE", "111", 5, "1"),
            (1300, 80, 2_999_000, "IDLE", "", 5, ""),
            (7900, 79, 2_970_000, "IDLE", "222", 1, "1"),
        ]:
            row = dict(base)
            row.update(
                wall_epoch=str(epoch), phase="running", battery_ac="false",
                battery_usb="false", battery_wireless="false", power_is_powered="false",
                power_plug_type="0", interactive="false", battery_level=str(level),
                charge_counter_uah=str(counter), temperature_tenths_c="280",
                deviceidle_deep=deep, deviceidle_light="IDLE", package_pid=pid,
                service_present=present, monitoring_enabled="true", monitoring_running="true",
                refresh_count=str(count), service_battery_level=str(level),
            )
            samples.append(row)
        endpoint = dict(base)
        endpoint.update(
            wall_epoch="8300", phase="post_deadline", battery_ac="false",
            battery_usb="false", battery_wireless="false", power_is_powered="false",
            power_plug_type="0", interactive="false", deviceidle_deep="IDLE",
            deviceidle_light="IDLE",
        )
        samples.append(endpoint)
        with (directory / "samples.tsv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, delimiter="\t")
            writer.writeheader()
            writer.writerows(samples)

        result = analyze(directory)
        assert result["status"] == "observed_with_gaps", result
        assert result["sampling_gaps"], result
        assert result["battery"]["level_change_points"] == -1, result
        assert result["service"]["restart_evidence_count"] == 1, result
        assert result["service"]["absent_sample_count"] == 0, result
        assert result["service"]["unknown_sample_count"] == 1, result
        assert result["doze"]["deep_idle_observed"] is True, result
        assert result["conditions"]["post_deadline_endpoint_compliant"] is True, result
        assert result["snapshot_consistency"]["mismatch_count"] == 0, result
    print("self-test: ok")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", nargs="?", type=Path, help="pulled recorder directory")
    parser.add_argument("--report", type=Path, help="Markdown output path")
    parser.add_argument("--summary", type=Path, help="JSON output path")
    parser.add_argument("--self-test", action="store_true", help="run the embedded synthetic check")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0
    if args.directory is None:
        parser.error("directory is required unless --self-test is used")

    directory = args.directory.resolve()
    summary = analyze(directory)
    report_text = render_report(summary, directory)
    report_path = args.report or directory / "analysis-report.md"
    summary_path = args.summary or directory / "summary.json"
    report_path.write_text(report_text, encoding="utf-8")
    summary_path.write_text(
        json.dumps(summary, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"status": summary["status"], "report": str(report_path), "summary": str(summary_path)}))
    return 0 if summary["status"] == "observed" else 1


if __name__ == "__main__":
    raise SystemExit(main())

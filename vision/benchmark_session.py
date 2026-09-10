"""Summarize a pose session, optionally exporting CSV. --static requires a stationary recording."""
import argparse
import csv
import json
import math
import pathlib
import statistics


def summarize(path, static=False, csv_path=None):
    if csv_path and pathlib.Path(csv_path).resolve() == pathlib.Path(path).resolve():
        raise ValueError("CSV output must be different from the input session")
    count = invalid = 0
    first = last = None
    latencies, knees = [], []
    output = open(csv_path, "w", newline="", encoding="utf-8") if csv_path else None
    writer = None
    try:
        with open(path, encoding="utf-8") as source:
            header = json.loads(source.readline())
            if header.get("version") != 1 or header.get("type") != "session":
                raise ValueError("Not a supported NOVA pose session")
            for line in source:
                sample = json.loads(line)
                if sample.get("sessionId") != header["sessionId"] or sample.get("type") != "pose":
                    raise ValueError("Invalid session sample")
                pose = sample["pose"]
                timestamp = pose["timestampMs"]
                if last is not None and timestamp < last:
                    raise ValueError("Session timestamps are not ordered")
                if first is None:
                    first = timestamp
                last = timestamp
                count += 1
                if not pose["trackingValid"]:
                    invalid += 1
                elif static:
                    knees.append(math.degrees(pose["rawKneeAngleRad"]))
                latencies.append(max(0, sample["receivedAtMs"] - timestamp))
                if output:
                    row = {"sessionId": header["sessionId"], "keyframeLabel": sample["keyframeLabel"],
                           "receivedAtMs": sample["receivedAtMs"], **pose}
                    for field in ("landmarks", "worldLandmarks"):
                        row[field] = json.dumps(row[field], separators=(",", ":"))
                    if writer is None:
                        writer = csv.DictWriter(output, fieldnames=list(row))
                        writer.writeheader()
                    writer.writerow(row)
        duration = (last - first) / 1000 if count > 1 else 0
        result = {"frames": count, "durationSeconds": duration,
                  "observedFps": (count - 1) / duration if duration else 0,
                  "trackingLossPercent": 100 * invalid / count if count else 0,
                  "medianCaptureToJavaMs": statistics.median(latencies) if latencies else None}
        if static:
            result["staticRawKneeRmsDegrees"] = statistics.pstdev(knees) if len(knees) > 1 else None
        return result
    finally:
        if output:
            output.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("session")
    parser.add_argument("--static", action="store_true")
    parser.add_argument("--csv", dest="csv_path")
    args = parser.parse_args()
    print(json.dumps(summarize(args.session, args.static, args.csv_path), indent=2))

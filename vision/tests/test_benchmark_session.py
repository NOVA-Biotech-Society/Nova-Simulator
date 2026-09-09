import importlib.util
import json
import pathlib
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("benchmark", pathlib.Path(__file__).parents[1] / "benchmark_session.py")
benchmark = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(benchmark)


class BenchmarkTests(unittest.TestCase):
    def test_known_capture_metrics_and_csv(self):
        with tempfile.TemporaryDirectory() as directory:
            session = pathlib.Path(directory) / "sample.jsonl"
            output = pathlib.Path(directory) / "output.csv"
            records = [{"version": 1, "type": "session", "sessionId": "test"}]
            for i in range(3):
                records.append({"type": "pose", "sessionId": "test", "keyframeLabel": "Standing",
                                "receivedAtMs": 1050 + i * 50,
                                "pose": {"timestampMs": 1000 + i * 50, "trackingValid": i < 2,
                                         "rawKneeAngleRad": 0, "landmarks": [], "worldLandmarks": []}})
            session.write_text("\n".join(json.dumps(record) for record in records), encoding="utf-8")
            result = benchmark.summarize(session, True, output)
            self.assertEqual(result["observedFps"], 20)
            self.assertEqual(result["medianCaptureToJavaMs"], 50)
            self.assertAlmostEqual(result["trackingLossPercent"], 100 / 3)
            self.assertEqual(result["staticRawKneeRmsDegrees"], 0)
            self.assertIn("rawKneeAngleRad", output.read_text())
            with self.assertRaises(ValueError):
                benchmark.summarize(session, csv_path=session)
            self.assertEqual(len(session.read_text().splitlines()), 4)


if __name__ == "__main__":
    unittest.main()

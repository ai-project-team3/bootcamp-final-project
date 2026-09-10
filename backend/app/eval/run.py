"""Shared evaluation runner. Built by role 3, used by everyone.

    python -m app.eval.run --fixtures fixtures/

Reads labelled fixtures, calls each role's function, prints the metric table:
matching F1 (per utterance), state accuracy (item x timestep), relapse
recall and precision (per EVENT, not per timestep), required-item detection.

Changes whose trigger is "carryover" or "manual" are excluded from every metric.
"""

import argparse


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the evaluation suite.")
    parser.add_argument("--fixtures", default="fixtures/", help="fixture directory")
    args = parser.parse_args()
    raise SystemExit(f"Not implemented yet (fixtures={args.fixtures}). Owner: 역할 3, W1.")


if __name__ == "__main__":
    main()

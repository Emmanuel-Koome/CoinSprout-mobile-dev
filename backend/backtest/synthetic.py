"""SYNTHETIC data for tests and demos ONLY. Never report results from this file as findings about the NSE."""
from __future__ import annotations

import numpy as np
import pandas as pd


def make(n: int = 208, seed: int = 3, calm_vol: float = 0.006, storm_vol: float = 0.02, storm_from: int = 120, storm_to: int = 150) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    vol = np.full(n, calm_vol); vol[storm_from:storm_to] = storm_vol
    px = 150 * np.exp(np.cumsum(rng.normal(0.0002, vol)))
    return pd.DataFrame({"date": pd.bdate_range("2025-10-01", periods=n), "nse_index": px,
                         "tbill_yield": 15 + np.cumsum(rng.normal(0, 0.02, n)), "mmf_yield": 11.5 + np.cumsum(rng.normal(0, 0.01, n))})

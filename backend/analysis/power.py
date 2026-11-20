"""Re-derives the sample-size claims in Chapters 1 and 3 and shows what the Holm adjustment does to them.
Run:  python -m analysis.power"""
from __future__ import annotations

from statsmodels.stats.power import TTestIndPower

D, TARGET_USABLE, LOSS = 0.26, 480, 0.30
p = TTestIndPower()


def report() -> str:
    out = []
    out.append(f"Design: {TARGET_USABLE} usable responses, 2x2 -> a main effect compares {TARGET_USABLE // 2} vs {TARGET_USABLE // 2}; target d = {D}")
    for label, a in (("single test, alpha = .05 (what Chapters 1/3 state)", 0.05), ("Holm step 1 for TWO outcomes, alpha = .025", 0.025)):
        pw = p.power(effect_size=D, nobs1=TARGET_USABLE // 2, alpha=a, ratio=1)
        n = p.solve_power(effect_size=D, power=0.80, alpha=a, ratio=1)
        mde = p.solve_power(nobs1=TARGET_USABLE // 2, power=0.80, alpha=a, ratio=1)
        out.append(f"- {label}: power = {pw:.1%}; n/group for 80% = {n:.0f} (usable total {2 * n:.0f}, recruit {2 * n / (1 - LOSS):.0f} at {LOSS:.0%} loss); minimum detectable d = {mde:.3f}")
    out.append(f"Recruitment arithmetic: {TARGET_USABLE} / (1 - {LOSS}) = {TARGET_USABLE / (1 - LOSS):.0f}")
    return "\n".join(out)


if __name__ == "__main__":
    print(report())

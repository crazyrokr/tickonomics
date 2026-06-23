"""Transfer entropy (TE) estimation for directional information flow.

TE(Y -> X) quantifies how much the future of X is predicted by the past of Y beyond what X's own
past already explains. This implementation uses a discrete histogram (plugin / maximum-likelihood)
estimator with a **Miller-Madow bias correction**: the plugin estimator is biased upward for finite
samples, and Miller-Madow subtracts an O(1/N) term derived from the number of occupied bins in each
joint and marginal distribution. The binning rule (sqrt(N/10) + 1) is a heuristic; results are
approximate for small N and should be interpreted alongside the permutation p-value returned by
:func:`compute_transfer_entropy`.
"""

import numpy as np


def _transfer_entropy(source: np.ndarray, target: np.ndarray, lag: int = 1) -> float:
    n = len(source)
    x_future = target[lag:]
    x_past = target[:-lag]
    y_past = source[:-lag]

    bins = int(np.sqrt(n / 10)) + 1
    bins = max(bins, 3)

    joint_3d, _ = np.histogramdd(
        np.column_stack([x_future, x_past, y_past]),
        bins=bins,
    )

    joint_2d_xf_xp, _ = np.histogramdd(
        np.column_stack([x_future, x_past]),
        bins=bins,
    )

    joint_2d_xp_yp, _ = np.histogramdd(
        np.column_stack([x_past, y_past]),
        bins=bins,
    )

    marginal_xp, _ = np.histogram(x_past, bins=bins)

    total = n - lag

    p_joint = joint_3d / total
    p_xf_xp = joint_2d_xf_xp / total
    p_xp_yp = joint_2d_xp_yp / total
    p_xp = marginal_xp / total

    te = 0.0
    for i in range(bins):
        for j in range(bins):
            for k in range(bins):
                if p_joint[i, j, k] > 0 and p_xf_xp[i, j] > 0 and p_xp_yp[j, k] > 0 and p_xp[j] > 0:
                    p_cond_joint = p_joint[i, j, k] / p_xp_yp[j, k]
                    p_cond_marginal = p_xf_xp[i, j] / p_xp[j]
                    if p_cond_marginal > 0:
                        te += p_joint[i, j, k] * np.log(p_cond_joint / p_cond_marginal)

    # Miller-Madow bias correction. TE expands as
    #   H(Xf,Xp) - H(Xp) - H(Xf,Xp,Yp) + H(Xp,Yp);
    # applying the Miller-Madow term (K-1)/(2N) to each entropy and collecting the constant
    # offsets yields correction = (K_xfxp - K_xp - K_3 + K_xpyp) / (2N), where K_* are the counts
    # of occupied bins in each distribution and N is the sample count.
    k_3 = int(np.count_nonzero(joint_3d))
    k_xfxp = int(np.count_nonzero(joint_2d_xf_xp))
    k_xpyp = int(np.count_nonzero(joint_2d_xp_yp))
    k_xp = int(np.count_nonzero(marginal_xp))
    correction = (k_xfxp - k_xp - k_3 + k_xpyp) / (2.0 * total)

    return max(0.0, te + correction)


def compute_transfer_entropy(
        source: list[float], target: list[float], lag: int = 1, n_bootstraps: int = 1000
) -> dict:
    src = np.array(source)
    tgt = np.array(target)

    te_observed = _transfer_entropy(src, tgt, lag)

    rng = np.random.default_rng(42)
    te_null = np.zeros(n_bootstraps)
    for i in range(n_bootstraps):
        permuted = rng.permutation(src)
        te_null[i] = _transfer_entropy(permuted, tgt, lag)

    p_value = float(np.mean(te_null >= te_observed))

    return {
        "entropy_bits": te_observed,
        "p_value": p_value,
        "lag": lag,
        "is_significant": p_value < 0.05,
    }

package me.stageguard.obms.utils

import org.apache.commons.math3.analysis.solvers.BrentSolver
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.special.Beta

fun betaInvCDF(a: Double, b: Double, p: Double) =
    BrentSolver(1e-12).solve(100, { x -> Beta.regularizedBeta(x, a, b) - p }, 0.0, 1.0)

fun normalInvCDF(mean: Double, standardDeviation: Double, p: Double) =
    NormalDistribution(mean, standardDeviation).inverseCumulativeProbability(p)
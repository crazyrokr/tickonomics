"""Verdict enum for external validation test outcomes."""

from enum import Enum


class Verdict(str, Enum):
    CORRECT = "CORRECT"
    INCORRECT = "INCORRECT"
    UNVERIFIABLE = "UNVERIFIABLE"
    BUG_FOUND = "BUG_FOUND"

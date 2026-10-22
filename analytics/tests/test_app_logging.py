import logging

from app.config import LOG_LEVEL


def test_app_import_configures_app_logging():
    """Given the app config defines LOG_LEVEL, when app.main is imported, then app logging is configured at that level."""
    # Given
    assert LOG_LEVEL == "INFO"

    # When
    import app.main  # noqa: F401

    # Then
    assert logging.getLogger("app").getEffectiveLevel() <= logging.INFO

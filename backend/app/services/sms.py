"""SMS delivery abstraction. Dev logs the message; plug a real gateway (e.g. Africa's Talking) in for production."""
from __future__ import annotations

import logging

from ..config import settings

log = logging.getLogger("coinsprout.sms")


def send(phone: str, message: str) -> None:
    if settings.is_dev:
        log.warning("DEV SMS to %s: %s", phone, message)
    else:  # pragma: no cover
        raise NotImplementedError("Configure a real SMS gateway before running with COINSPROUT_ENV=prod")

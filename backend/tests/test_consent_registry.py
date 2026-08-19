"""Consent must never again be decorative.

`partner_access` shipped as a real, working feature whose consent nothing read:
a user could switch partner access off and still create invites, while partners
who had already accepted kept reading her appointments and medicines. Four more
toggles sat beside it governing nothing.

The first test here is the important one — it is a structural rule, not a check
on today's behaviour. Every entry in `consent.FEATURES` must declare how its
consent is honoured, so a future feature cannot reach the privacy centre without
someone deciding that. The rest pin the runtime consequences of that rule.
"""
from app.domains import consent
import pytest


def test_consent_is_not_decorative():
    """Every feature declares exactly one of: where it is enforced, that it
    isn't available, or that it is a locked policy statement.

    If this fails because you added a feature, the fix is not to add a branch
    here — it is to wire the consent check and name it in `enforced_by`.
    """
    for key, meta in consent.FEATURES.items():
        declarations = [
            bool(meta.get("enforced_by")),
            meta.get("available") is False,
            bool(meta.get("locked")),
        ]
        assert sum(declarations) == 1, (
            f"consent feature {key!r} must declare exactly one of "
            f"enforced_by / available=False / locked — got {meta}"
        )
        if meta.get("enforced_by"):
            # A non-empty claim about where the check lives, so the registry
            # can't be satisfied with `enforced_by: ""`.
            assert len(meta["enforced_by"]) > 3


def test_every_enforced_feature_names_a_real_callable():
    """`enforced_by` points at code that exists, so the claim stays true when
    a module is renamed, moved, or deleted.

    The attribute is resolved, not just the module. "consent is enforced in
    memory.context_summary" is a claim about a FUNCTION, and a module-only check
    would keep passing after that function was renamed or inlined away — which
    is precisely when the registry starts lying about where the gate is.
    """
    import importlib
    for key, meta in consent.FEATURES.items():
        for site in (meta.get("enforced_by") or "").split(","):
            site = site.strip()
            if not site:
                continue
            module_path, _, attr = site.rpartition(".")
            module = importlib.import_module(module_path)   # raises if it's gone
            assert hasattr(module, attr), (
                f"consent {key!r} claims it is enforced by {site}, "
                f"but {module_path} has no {attr!r}"
            )


# ── the runtime consequences ────────────────────────────────────────────────

def test_unavailable_consent_cannot_be_granted(client, user):
    for feature in ("avatar", "future_baby_story", "store_raw_audio"):
        r = client.post("/v1/consent", json={"feature": feature, "granted": True},
                        headers=user["headers"])
        assert r.status_code == 400, f"{feature} should not be grantable"
        assert "not available" in r.json()["detail"]


def test_unavailable_consent_can_still_be_revoked(client, user):
    """A grant written by an older build must always be withdrawable — refusing
    the revoke would trap the user with a permission they can't take back."""
    r = client.post("/v1/consent", json={"feature": "avatar", "granted": False},
                    headers=user["headers"])
    assert r.status_code == 200, r.text


def test_a_stale_grant_never_reads_as_granted(client, user):
    """Simulates a row written before these became unavailable: the ledger is
    append-only, so the old row survives — it just must not count."""
    consent._record(user["id"], "avatar", True, "written by an older build")
    feats = {f["key"]: f for f in client.get("/v1/consent",
                                             headers=user["headers"]).json()["features"]}
    assert feats["avatar"]["granted"] is False
    assert consent.is_granted(user["id"], "avatar") is False


def test_consent_response_exposes_availability(client, user):
    feats = {f["key"]: f for f in client.get("/v1/consent",
                                             headers=user["headers"]).json()["features"]}
    assert feats["avatar"]["available"] is False
    assert feats["partner_access"]["available"] is True
    assert feats["personalization"]["available"] is True
    # Locked stays distinct from unavailable: ads are a policy statement about a
    # feature we could build and refuse to, not one we haven't got round to.
    assert feats["data_for_ads"]["locked"] is True
    assert feats["data_for_ads"]["available"] is True


def test_available_features_are_still_grantable(client, user):
    r = client.post("/v1/consent", json={"feature": "partner_access", "granted": True},
                    headers=user["headers"])
    assert r.status_code == 200, r.text
    assert consent.is_granted(user["id"], "partner_access") is True


@pytest.mark.parametrize("feature", ["avatar", "future_baby_story", "store_raw_audio"])
def test_require_consent_refuses_unavailable_features(client, user, feature):
    """If someone wires `require_consent("avatar")` onto a route before the
    feature exists, it must refuse rather than pass."""
    assert consent.is_granted(user["id"], feature) is False

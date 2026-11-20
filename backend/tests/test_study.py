from collections import Counter

from app.config import settings
from app.study import instrument as ins

ADMIN = {"X-Admin-Key": "dev-admin-key"}
S = "/api/v1/study"


def screen(client, year=2, **over):
    body = {"consent": True, "age18OrOver": True, "enrolledStrathmoreUndergraduate": True, "usedMobileMoneyLast30Days": True, "yearOfStudy": year}
    body.update(over)
    return client.post(f"{S}/screen", json=body).json()


def baseline_body(h=3, t=3, i=3, literacy=("a", "c", "b")):
    return {"mobileMoneyFrequency": "daily", "fintechExperience": [5, 5, 5], "instrumentsUsed": ["sacco", "chama"], "previousInvestment": False,
            "literacy": list(literacy), "algoTrust": [6, 6, 2, 5], "goal": "school_fees", "timeHorizon": h, "riskPreference": t, "incomeStability": i}


def outcomes_body(attn=None, ai="yes"):
    L = settings.study_likert_points
    return {"trust": [5] * 5, "adoption": [5] * 4, "fit": [5] * 3, "perceivedPersonalisation": [5, 5, 3], "manipulationAiNoticed": ai,
            "attentionResponse": L - 1 if attn is None else attn}


def run_participant(client, year=2, **bl):
    pid = screen(client, year)["participantId"]
    assert client.post(f"{S}/{pid}/baseline", json=baseline_body(**bl)).status_code == 200
    stim = client.post(f"{S}/{pid}/assign").json()["stimulus"]
    return pid, stim


def test_screening_blocks_ineligible_and_logs_anonymously(client):
    for field, reason in [("consent", "no_consent"), ("age18OrOver", "under_18"), ("enrolledStrathmoreUndergraduate", "not_enrolled"),
                          ("usedMobileMoneyLast30Days", "no_recent_mobile_money")]:
        r = screen(client, **{field: False})
        assert r["eligible"] is False and r["reason"] == reason and "participantId" not in r
    assert screen(client)["eligible"] is True
    prog = client.get("/api/v1/admin/study/progress", headers=ADMIN).json()
    assert prog["screening"]["eligible"] == 1 and prog["screening"]["excluded:under_18"] == 1


def test_access_code_when_configured(client):
    object.__setattr__(settings, "study_access_code", "SU-2026")      # Settings is a frozen dataclass; restore afterwards
    try:
        assert screen(client) == {"eligible": False, "reason": "bad_code", "message": "That access code is not valid."}
        assert screen(client, accessCode="WRONG")["eligible"] is False
        assert screen(client, accessCode="SU-2026")["eligible"] is True
        assert client.get(f"{S}/instrument").json()["accessCodeRequired"] is True
    finally:
        object.__setattr__(settings, "study_access_code", "")


def test_instrument_never_leaks_answer_key_and_is_flagged_draft(client):
    inst = client.get(f"{S}/instrument").json()
    assert inst["status"] == "DRAFT_WORDING" and all("correct" not in q for q in inst["literacy"])
    cfg = client.get(f"{S}/config").json()
    assert cfg["genericAllocation"] == {"mmf": 41, "tbill": 23, "unit": 27, "cash": 9} and len(cfg["instrumentHash"]) == 16


def test_must_finish_baseline_before_assignment_and_assignment_is_idempotent(client):
    pid = screen(client)["participantId"]
    assert client.post(f"{S}/{pid}/assign").status_code == 409
    client.post(f"{S}/{pid}/baseline", json=baseline_body())
    a = client.post(f"{S}/{pid}/assign").json()
    b = client.post(f"{S}/{pid}/assign").json()
    assert a == b                                                       # a page refresh cannot re-randomise
    assert client.post(f"{S}/{pid}/baseline", json=baseline_body()).status_code == 409   # baseline locked after exposure


def test_stratified_permuted_blocks_balance_every_block_of_four(client):
    arms_by_year = {2: [], 3: []}
    for k in range(16):                                                  # interleave years: strata must be independent
        year = 2 if k % 2 == 0 else 3
        pid, _ = run_participant(client, year)
        arms_by_year[year].append(pid)
    from app.db import SessionLocal
    from app.models import StudyParticipant
    with SessionLocal() as db:
        for year in (2, 3):
            rows = sorted(db.query(StudyParticipant).filter_by(year_of_study=year).all(), key=lambda r: r.stratum_seq)
            assert [r.stratum_seq for r in rows] == list(range(8))
            for blk in (rows[:4], rows[4:]):
                assert Counter((r.personalized, r.ai_label) for r in blk) == Counter({(True, True): 1, (True, False): 1, (False, True): 1, (False, False): 1})


def test_layout_is_identical_across_conditions_and_only_intended_parts_differ(client):
    stimuli = [run_participant(client, 2)[1] for _ in range(4)]
    for s in stimuli:
        assert set(s) == {"headline", "allocation", "whyText", "aiLabelText", "disclaimer"}
        assert sum(s["allocation"].values()) == 100 and "guaranteed" in s["disclaimer"]
    assert sum(s["aiLabelText"] is not None for s in stimuli) == 2        # two of every four carry the label
    generic = [s for s in stimuli if s["whyText"].startswith("A standard")]
    assert len(generic) == 2 and all(g["allocation"] == {"mmf": 41, "tbill": 23, "unit": 27, "cash": 9} for g in generic)


def test_personalised_stimulus_reflects_answers_and_flags_when_it_equals_the_generic_split(client):
    from app.db import SessionLocal
    from app.models import StudyParticipant
    seen = {}
    for _ in range(40):                                                  # keep going until we have seen a personalised participant of each kind
        pid, stim = run_participant(client, 4, h=1, t=1, i=1)            # cautious short-horizon respondent
        pid2, stim2 = run_participant(client, 4, h=2, t=2, i=2)          # mid-point respondent == reference profile
        with SessionLocal() as db:
            for q, s in ((pid, stim), (pid2, stim2)):
                p = db.get(StudyParticipant, q)
                if p.personalized:
                    seen[q] = (p.same_as_generic, s)
        kinds = {v[0] for v in seen.values()}
        if kinds == {True, False}:
            break
    assert {v[0] for v in seen.values()} == {True, False}
    cautious = [v[1] for v in seen.values() if not v[0]][0]
    assert cautious["allocation"]["mmf"] > 41 and cautious["allocation"]["unit"] < 10 and "school fees" in cautious["whyText"]


def test_outcomes_scoring_attention_manipulation_and_usability(client):
    pid, _ = run_participant(client, 1)
    r = client.post(f"{S}/{pid}/outcomes", json=outcomes_body())
    assert r.status_code == 200 and client.post(f"{S}/{pid}/outcomes", json=outcomes_body()).status_code == 409
    pid2, _ = run_participant(client, 1)
    client.post(f"{S}/{pid2}/outcomes", json=outcomes_body(attn=1, ai="no"))        # failed attention check
    prog = client.get("/api/v1/admin/study/progress", headers=ADMIN).json()
    assert prog["completed"] == 2 and prog["usable"] == 1 and prog["usableTarget"] == 480


def test_reverse_scoring_and_validation(client):
    pid, _ = run_participant(client, 2)
    L = settings.study_likert_points
    bad = outcomes_body(); bad["trust"] = [L + 1] * 5
    assert client.post(f"{S}/{pid}/outcomes", json=bad).status_code == 422
    short = outcomes_body(); short["fit"] = [3, 3]
    assert client.post(f"{S}/{pid}/outcomes", json=short).status_code == 422
    # perceived-personalisation item 3 is reverse scored: answers [5,5,3] on a 7-pt scale -> (5+5+(8-3))/3
    client.post(f"{S}/{pid}/outcomes", json=outcomes_body())
    csv_text = client.get("/api/v1/admin/study/export.csv", headers=ADMIN).text
    header, row = csv_text.strip().splitlines()[:2]
    col = dict(zip(header.split(","), row.split(",")))
    assert float(col["perceived_personalisation_mean"]) == round((5 + 5 + (L + 1 - 3)) / 3, 4)
    assert float(col["algo_trust_mean"]) == round((6 + 6 + (L + 1 - 2) + 5) / 4, 4)


def test_literacy_scoring_and_dont_know_is_incorrect(client):
    pid = screen(client)["participantId"]
    client.post(f"{S}/{pid}/baseline", json=baseline_body(literacy=("a", "dk", "b")))
    csv_text = client.get("/api/v1/admin/study/export.csv", headers=ADMIN).text.strip().splitlines()
    col = dict(zip(csv_text[0].split(","), csv_text[1].split(",")))
    assert col["literacy_score"] == "2" and col["status"] == "baseline" and col["participates_formal"] == "1"
    assert client.post(f"{S}/{pid}/baseline", json={**baseline_body(), "literacy": ["a", "c"]}).status_code == 422
    assert client.post(f"{S}/{pid}/baseline", json={**baseline_body(), "instrumentsUsed": ["bitcoin"]}).status_code == 422


def test_withdrawal_deletes_everything_and_export_is_pseudonymous_and_admin_only(client):
    pid, _ = run_participant(client, 2)
    client.post(f"{S}/{pid}/outcomes", json=outcomes_body())
    assert client.get("/api/v1/admin/study/export.csv").status_code == 403
    text = client.get("/api/v1/admin/study/export.csv", headers=ADMIN).text
    assert pid not in text and "phone" not in text.lower() and text.count("\n") == 2
    assert client.delete(f"{S}/{pid}").status_code == 204
    assert client.get("/api/v1/admin/study/export.csv", headers=ADMIN).text.count("\n") == 1      # header only
    assert client.post(f"{S}/{pid}/assign").status_code == 404
    assert client.delete(f"{S}/{pid}").status_code == 204                                            # idempotent


def test_participants_are_not_linked_to_app_users(client):
    from app.models import StudyParticipant
    cols = {c.name for c in StudyParticipant.__table__.columns}
    assert not (cols & {"phone", "user_id", "email", "ip", "ip_address"})


def test_study_page_is_served(client):
    r = client.get("/study")
    assert r.status_code == 200 and "text/html" in r.headers["content-type"]

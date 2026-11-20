"""The questionnaire definition (single source of truth for the API, the web page and the analysis scripts).

!!! STATUS: DRAFT WORDING !!!
Chapter 3 leaves the exact items open ("[Specify the source scale or the items adapted]"). The statements below are
my plain-language drafts so the pipeline can run end to end. Before data collection you MUST replace each scale's
wording with the validated items you actually cite (Komiak & Benbasat 2006; Lourenco et al. 2020; Davis 1989;
Nourallah 2023; a published algorithm-trust scale) and pilot them. Only the three literacy questions follow a
published instrument (the Lusardi & Mitchell 'Big Three', adapted to KES).
"""
from __future__ import annotations

import hashlib
import json

from ..config import settings

STATUS = "DRAFT_WORDING"

LIKERT_LABELS = {
    5: {1: "Strongly disagree", 2: "Disagree", 3: "Neutral", 4: "Agree", 5: "Strongly agree"},
    7: {1: "Strongly disagree", 2: "Disagree", 3: "Somewhat disagree", 4: "Neutral", 5: "Somewhat agree", 6: "Agree", 7: "Strongly agree"},
}

LITERACY = [
    {"id": "lit_interest", "correct": "a",
     "text": "Suppose you put KES 10,000 in a savings account that pays 2% interest per year. If you leave the money there for 5 years, how much will be in the account?",
     "options": [["a", "More than KES 10,200"], ["b", "Exactly KES 10,200"], ["c", "Less than KES 10,200"], ["dk", "I don't know"]]},
    {"id": "lit_inflation", "correct": "c",
     "text": "Suppose your savings account pays 1% interest per year and prices rise by 2% per year. After 1 year, what could you buy with the money in the account?",
     "options": [["a", "More than today"], ["b", "Exactly the same as today"], ["c", "Less than today"], ["dk", "I don't know"]]},
    {"id": "lit_risk", "correct": "b",
     "text": "True or false: buying shares in a single company is usually safer than buying a fund that holds shares in many companies.",
     "options": [["a", "True"], ["b", "False"], ["dk", "I don't know"]]},
]

# reverse=True items are reverse-scored before averaging.
SCALES = {
    "algo_trust": [
        {"id": "at1", "text": "I am comfortable making money decisions using advice from a computer program."},
        {"id": "at2", "text": "Advice from a computer program is usually as reliable as advice from a person."},
        {"id": "at3", "text": "I would rather follow my own judgement than a computer's advice about money.", "reverse": True},
        {"id": "at4", "text": "If a computer program and a person gave me different money advice, I would follow the program."},
    ],
    "fintech": [
        {"id": "ft1", "text": "I am comfortable using new mobile money or banking apps."},
        {"id": "ft2", "text": "I often try new financial apps or services."},
        {"id": "ft3", "text": "I can usually work out a new financial app without help."},
    ],
    "trust": [
        {"id": "tr1", "text": "I believe this recommendation is reliable."},
        {"id": "tr2", "text": "I believe this recommendation was made with my interests in mind."},
        {"id": "tr3", "text": "I would feel comfortable relying on this recommendation."},
        {"id": "tr4", "text": "I believe this recommendation is honest about the risks involved."},
        {"id": "tr5", "text": "I trust the way this recommendation was produced."},
    ],
    "adoption": [
        {"id": "ad1", "text": "If a tool like this were available, I would use it to decide how to split my savings."},
        {"id": "ad2", "text": "I would recommend a tool like this to a friend."},
        {"id": "ad3", "text": "If a tool like this were available, I would try it within the next six months."},
        {"id": "ad4", "text": "Using a tool like this would be a good idea."},
    ],
    "fit": [
        {"id": "fi1", "text": "This recommendation suits my situation."},
        {"id": "fi2", "text": "This recommendation matches my goals."},
        {"id": "fi3", "text": "This recommendation is right for someone like me."},
    ],
    # Manipulation check for the personalisation factor (Chapter 2 separates personalisation from perceived fit).
    "perceived_personalisation": [
        {"id": "pp1", "text": "This recommendation was made specifically for me."},
        {"id": "pp2", "text": "This recommendation takes my answers into account."},
        {"id": "pp3", "text": "Other students would probably see exactly the same recommendation.", "reverse": True},
    ],
}

MOBILE_MONEY_FREQUENCY = ["daily", "weekly", "monthly", "rarely"]
FORMAL_INSTRUMENTS = ["bank_savings", "sacco", "money_market_fund", "treasury_bills", "shares", "pension"]
INFORMAL_INSTRUMENTS = ["chama", "savings_group", "merry_go_round"]
INSTRUMENT_LABELS = {
    "bank_savings": "Bank savings account", "sacco": "SACCO", "money_market_fund": "Money market fund",
    "treasury_bills": "Treasury bills / bonds", "shares": "Shares (NSE)", "pension": "Pension / retirement scheme",
    "chama": "Chama", "savings_group": "Savings group", "merry_go_round": "Merry-go-round",
}
GOALS = {"emergency_fund": "an emergency fund", "school_fees": "school fees", "start_business": "starting a business",
         "buy_asset": "buying something big (e.g. land, laptop)", "general_savings": "general savings"}
HORIZONS = {1: "less than a year", 2: "one to three years", 3: "more than three years"}
TOLERANCES = {1: "you prefer to avoid losses", 2: "you can accept some ups and downs", 3: "you can accept big ups and downs for higher growth"}
INCOME = {1: "irregular", 2: "mostly steady", 3: "very steady"}

AI_LABEL_TEXT = "This recommendation was generated with the help of artificial intelligence (AI)."
DISCLAIMER = "For illustration only. No real money is involved and returns are never guaranteed."

DEBRIEF = (
    "Thank you for taking part. This study asked how two features of an investment recommendation - whether it is "
    "personalised and whether it is labelled as AI-generated - affect trust and willingness to use a digital investment "
    "tool. Different participants saw different versions: some saw a split based on their own answers, others a standard "
    "split; some saw an AI label, others did not. The recommendation you saw was an illustration only; no real money was "
    "involved and it is not financial advice. Your answers are stored under a random code (no name, phone number or "
    "M-Pesa details). You can withdraw your responses at any time using the button on this page. [DRAFT - replace with "
    "text approved by your ethics committee, including supervisor and contact details.]"
)


def scale_ids(name: str) -> list[str]:
    return [i["id"] for i in SCALES[name]]


def reverse_flags(name: str) -> list[bool]:
    return [bool(i.get("reverse")) for i in SCALES[name]]


def instrument_payload() -> dict:
    L = settings.study_likert_points
    return {
        "status": STATUS, "likertPoints": L, "likertLabels": {str(k): v for k, v in LIKERT_LABELS[L].items()},
        "literacy": [{k: v for k, v in q.items() if k != "correct"} for q in LITERACY],     # never send the answer key
        "scales": {n: [{"id": i["id"], "text": i["text"]} for i in items] for n, items in SCALES.items()},
        "attention": {"id": "attn", "text": f"To show you are reading carefully, please select \"{LIKERT_LABELS[L][L - 1]}\" for this statement."},
        "mobileMoneyFrequency": MOBILE_MONEY_FREQUENCY,
        "instruments": [{"id": k, "label": INSTRUMENT_LABELS[k], "formal": k in FORMAL_INSTRUMENTS} for k in FORMAL_INSTRUMENTS + INFORMAL_INSTRUMENTS],
        "goals": GOALS, "horizons": {str(k): v for k, v in HORIZONS.items()}, "tolerances": {str(k): v for k, v in TOLERANCES.items()},
        "incomeStability": {str(k): v for k, v in INCOME.items()},
        "accessCodeRequired": bool(settings.study_access_code),
    }


def instrument_hash() -> str:
    canonical = json.dumps({"p": instrument_payload(), "rev": {n: reverse_flags(n) for n in SCALES}, "key": [q["correct"] for q in LITERACY]}, sort_keys=True)
    return hashlib.sha256(canonical.encode()).hexdigest()[:16]

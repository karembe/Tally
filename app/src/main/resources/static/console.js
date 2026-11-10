"use strict";

const view = document.getElementById("view");
let adminToken = "";

function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
        if (v === null || v === undefined || v === false) continue;
        if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
        else if (k === "class") el.className = v;
        else el.setAttribute(k, v === true ? "" : v);
    }
    for (const c of children.flat()) {
        if (c === null || c === undefined || c === false) continue;
        el.append(c instanceof Node ? c : String(c));
    }
    return el;
}

async function api(path, options = {}) {
    const res = await fetch(path, {
        ...options,
        headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}), ...options.headers },
    });
    const body = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) throw new Error(body?.detail || body?.title || `HTTP ${res.status}`);
    return { status: res.status, body };
}

// Amounts are integer minor units; format with string ops so no float ever touches money.
function money(minor, currency) {
    const neg = minor < 0;
    const digits = String(Math.abs(minor)).padStart(3, "0");
    const major = digits.slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
    return `${neg ? "−" : ""}${major}.${digits.slice(-2)}${currency ? " " + currency : ""}`;
}

function parseMinor(text) {
    const m = /^\s*(\d{1,13})(?:[.,](\d{1,2}))?\s*$/.exec(text);
    if (!m) return null;
    const minor = Number(m[1]) * 100 + Number((m[2] || "0").padEnd(2, "0"));
    return minor > 0 ? minor : null;
}

const when = (iso) => new Date(iso).toLocaleString(undefined, { dateStyle: "short", timeStyle: "medium" });
const shortHash = (hex) => `${hex.slice(0, 10)}…${hex.slice(-6)}`;
const shortId = (id) => id.slice(0, 8);

function render(...nodes) {
    view.replaceChildren(...nodes.flat());
    view.focus({ preventScroll: true });
}

function failure(err) {
    render(h("h1", {}, "Could not load"), h("p", { class: "error" }, err.message));
}

async function refreshSeal() {
    const seal = document.getElementById("seal");
    const text = document.getElementById("seal-text");
    try {
        const { body: i } = await api("/integrity");
        const ok = i.chainValid && i.sumOfBalances === 0 && i.negativeUserAccounts === 0;
        seal.className = `seal ${ok ? "ok" : "bad"}`;
        text.textContent = ok ? `Chain verified to event #${i.headSeq}` : "Integrity check failed";
        return i;
    } catch (err) {
        seal.className = "seal bad";
        text.textContent = "Ledger unreachable";
        throw err;
    }
}

function figure(label, value, note, tone) {
    return h("div", { class: "figure" },
        h("dt", {}, label),
        h("dd", { class: tone || "" }, value, note && h("span", { class: "note" }, note)));
}

function transfersTable(rows) {
    if (!rows.length) return h("p", { class: "empty" }, "No transfers yet.");
    return h("div", { class: "table-wrap" }, h("table", {},
        h("thead", {}, h("tr", {},
            h("th", {}, "Time"), h("th", {}, "From"), h("th", {}, "To"),
            h("th", { class: "num" }, "Amount"), h("th", {}, "Status"), h("th", { class: "num" }, "Event"))),
        h("tbody", {}, rows.map((t) => h("tr", {},
            h("td", { class: "muted" }, when(t.createdAt)),
            h("td", {}, h("a", { href: `#/accounts/${t.fromAccount}` }, t.fromOwner)),
            h("td", {}, h("a", { href: `#/accounts/${t.toAccount}` }, t.toOwner)),
            h("td", { class: "num" }, money(t.amount, t.currency)),
            h("td", {}, h("span", { class: "tag" }, t.status.toLowerCase())),
            h("td", { class: "num" }, h("a", { href: `#/transfers/${t.id}` }, `#${t.eventSeq}`)))))));
}

function integrityFigures(i) {
    return h("dl", { class: "figures" },
        figure("Sum of all balances", money(i.sumOfBalances), i.sumOfBalances === 0 ? "Σ = 0, double entry holds" : "Σ ≠ 0, money created or lost",
            i.sumOfBalances === 0 ? "good" : "fail"),
        figure("Transfers", i.transfers.toLocaleString(), `${i.entries.toLocaleString()} ledger entries`),
        figure("Chain head", `#${i.headSeq}`, i.headSeq ? shortHash(i.headHash) : "empty chain", i.chainValid ? "good" : "fail"),
        figure("Overdrawn user accounts", String(i.negativeUserAccounts), i.negativeUserAccounts ? "should never happen" : "none",
            i.negativeUserAccounts ? "fail" : ""));
}

async function overview() {
    const [i, { body: recent }] = await Promise.all([refreshSeal(), api("/transfers?limit=8")]);
    render(
        h("h1", {}, "Overview"),
        h("p", { class: "lede" }, `Checked by the ledger itself at ${when(i.checkedAt)}. An independent verifier comes in phase 4.`),
        integrityFigures(i),
        h("h2", {}, "Recent transfers"),
        transfersTable(recent),
        h("p", { class: "more" }, h("a", { href: "#/transfers" }, "See all transfers or send one")));
}

async function accounts() {
    const { body: list } = await api("/accounts");
    render(
        h("h1", {}, "Accounts"),
        h("p", { class: "lede" }, `${list.length} accounts. System accounts may go negative; user accounts never can.`),
        h("div", { class: "table-wrap" }, h("table", {},
            h("thead", {}, h("tr", {}, h("th", {}, "Owner"), h("th", {}, "Kind"), h("th", {}, "Id"), h("th", { class: "num" }, "Balance"))),
            h("tbody", {}, list.map((a) => h("tr", {},
                h("td", {}, h("a", { href: `#/accounts/${a.id}` }, a.ownerName)),
                h("td", {}, h("span", { class: `tag ${a.kind === "USER" ? "" : "system"}` }, a.kind.toLowerCase())),
                h("td", { class: "mono muted" }, shortId(a.id)),
                h("td", { class: `num ${a.balance < 0 ? "debit" : ""}` }, money(a.balance, a.currency))))))));
}

async function account(id) {
    const { body: a } = await api(`/accounts/${encodeURIComponent(id)}`);
    render(
        h("p", { class: "muted" }, h("a", { href: "#/accounts" }, "← Accounts")),
        h("h1", {}, a.ownerName),
        h("p", { class: "lede mono" }, `${a.kind === "USER" ? "User" : "System"} account ${a.id}`),
        h("dl", { class: "figures" },
            figure("Balance", money(a.balance, a.currency), `opened ${when(a.createdAt)}`, a.balance < 0 ? "fail" : "")),
        h("h2", {}, "Recent entries"),
        a.entries.length ? h("div", { class: "table-wrap" }, h("table", {},
            h("thead", {}, h("tr", {}, h("th", {}, "Time"), h("th", {}, "Transfer"), h("th", {}, "Side"), h("th", { class: "num" }, "Amount"))),
            h("tbody", {}, a.entries.map((e) => h("tr", {},
                h("td", { class: "muted" }, when(e.createdAt)),
                h("td", { class: "mono" }, h("a", { href: `#/transfers/${e.transferId}` }, shortId(e.transferId))),
                h("td", { class: e.direction === "CREDIT" ? "credit" : "debit" }, e.direction.toLowerCase()),
                h("td", { class: "num" }, money(e.amount, a.currency)))))))
            : h("p", { class: "empty" }, "No entries yet."));
}

function receipt(status, r) {
    return h("div", { class: "receipt" },
        h("dl", {},
            h("dt", {}, "Result"), h("dd", {}, status === 201 ? "committed" : "replayed (same key, nothing moved twice)"),
            h("dt", {}, "Transfer"), h("dd", {}, h("a", { href: `#/transfers/${r.id}` }, r.id)),
            h("dt", {}, "Event"), h("dd", {}, `#${r.eventSeq}`),
            h("dt", {}, "Time"), h("dd", {}, when(r.createdAt))));
}

function moneyForm({ fields, submitLabel, send }) {
    let key = crypto.randomUUID();
    const out = h("div", {});
    const keyLine = h("span", { class: "mono" }, key);
    const button = h("button", { class: "btn", type: "submit" }, submitLabel);
    const form = h("form", {
        onsubmit: async (ev) => {
            ev.preventDefault();
            button.disabled = true;
            try {
                const { status, body } = await send(form, key);
                out.replaceChildren(receipt(status, body));
                refreshSeal().catch(() => {});
            } catch (err) {
                out.replaceChildren(h("p", { class: "error" }, err.message));
            } finally {
                button.disabled = false;
            }
        },
    }, fields,
        h("p", { class: "muted" }, "Idempotency key ", keyLine, " — resubmitting replays instead of paying twice. ",
            h("button", { class: "btn quiet", type: "button", onclick: () => { key = crypto.randomUUID(); keyLine.textContent = key; out.replaceChildren(); } }, "New key")),
        button);
    return [form, out];
}

function accountSelect(name, list) {
    return h("select", { name, required: true },
        h("option", { value: "" }, "Choose…"),
        list.map((a) => h("option", { value: a.id }, `${a.ownerName} (${money(a.balance, a.currency)})`)));
}

async function transfers() {
    const [{ body: list }, { body: recent }] = await Promise.all([api("/accounts"), api("/transfers?limit=50")]);
    const users = list.filter((a) => a.kind === "USER");
    const [form, out] = moneyForm({
        submitLabel: "Send transfer",
        fields: [
            h("label", {}, "From", accountSelect("from", users)),
            h("label", {}, "To", accountSelect("to", users)),
            h("label", {}, "Amount", h("input", { name: "amount", class: "amount", inputmode: "decimal", placeholder: "0.00", required: true, autocomplete: "off" })),
        ],
        send: (f, key) => {
            const from = users.find((a) => a.id === f.from.value);
            const amount = parseMinor(f.amount.value);
            if (!amount) throw new Error("Amount must be positive, at most two decimals.");
            return api("/transfers", {
                method: "POST",
                headers: { "Idempotency-Key": key },
                body: JSON.stringify({ fromAccountId: f.from.value, toAccountId: f.to.value, amount, currency: from.currency }),
            });
        },
    });
    render(
        h("h1", {}, "Transfers"),
        h("p", { class: "lede" }, "Each transfer writes two entries and one hashed event in a single database transaction."),
        h("div", { class: "split" }, h("section", {}, h("h2", {}, "New transfer"), form, out)),
        h("h2", {}, "Latest 50"),
        transfersTable(recent));
}

async function transfer(id) {
    const { body: d } = await api(`/transfers/${encodeURIComponent(id)}`);
    const t = d.transfer;
    render(
        h("p", { class: "muted" }, h("a", { href: "#/transfers" }, "← Transfers")),
        h("h1", {}, money(t.amount, t.currency)),
        h("p", { class: "lede mono" }, `${t.status === "COMMITTED" ? "Committed" : t.status} as event #${t.eventSeq} on ${when(t.createdAt)}`),
        h("h2", {}, "Entries"),
        h("div", { class: "table-wrap" }, h("table", {},
            h("thead", {}, h("tr", {}, h("th", {}, "Account"), h("th", {}, "Side"), h("th", { class: "num" }, "Amount"))),
            h("tbody", {}, d.entries.map((e) => h("tr", {},
                h("td", { class: "mono" }, h("a", { href: `#/accounts/${e.accountId}` }, e.accountId)),
                h("td", { class: e.direction === "CREDIT" ? "credit" : "debit" }, e.direction.toLowerCase()),
                h("td", { class: "num" }, money(e.amount, t.currency))))))),
        h("p", { class: "muted" }, "Debits and credits always sum to the same amount."));
}

async function chain() {
    const i = await refreshSeal();
    const list = h("ol", { class: "chain" });
    const more = h("button", { class: "btn quiet more", type: "button" }, "Older events");
    let previous = null;

    async function load(before) {
        more.disabled = true;
        const { body: events } = await api(`/events?limit=50${before ? `&before=${before}` : ""}`);
        for (const e of events) {
            const linked = previous && previous.prevHash === e.hash;
            if (previous) list.lastChild.querySelector(".check").textContent = linked ? `links to #${e.seq} ✓` : `does not link to #${e.seq}`;
            list.append(h("li", {},
                h("div", { class: "seq" }, `#${e.seq}`),
                h("div", {},
                    h("div", { class: "what" },
                        h("a", { href: `#/transfers/${e.transferId}` }, `${e.fromOwner} paid ${e.toOwner}`), " ",
                        h("span", { class: "mono" }, money(e.amount, e.currency)), " on ",
                        h("span", { class: "muted" }, when(e.createdAt))),
                    h("div", { class: "link" }, h("span", {}, "prev"), h("span", {}, e.prevHash), h("span", {}, "hash"), h("span", {}, e.hash)),
                    h("div", { class: "check match mono" }, e.seq === 1 ? "first event (genesis)" : ""))));
            previous = e;
        }
        more.hidden = events.length < 50;
        more.disabled = false;
    }

    more.addEventListener("click", () => load(previous.seq).catch(failure));
    render(
        h("h1", {}, "Event chain"),
        h("p", { class: "lede" }, "Every event stores the hash of the one before it. Change one row and every hash after it stops matching."),
        h("div", { class: `notice ${i.chainValid ? "" : "bad"}` },
            h("strong", {}, i.chainValid ? "Chain verified from genesis" : `Chain broken at event #${i.brokenAtSeq}`),
            i.chainValid ? `${i.eventsChecked} events recomputed, head matches chain_head.` : i.problem,
            h("div", { class: "muted" }, "This check runs inside the ledger, so a database admin could fool it. The independent verifier arrives in phase 4.")),
        list, more);
    await load(null);
}

async function funding() {
    const { body: list } = await api("/accounts");
    const users = list.filter((a) => a.kind === "USER");
    const [form, out] = moneyForm({
        submitLabel: "Fund account",
        fields: [
            h("label", {}, "Admin token", h("input", { name: "token", type: "password", value: adminToken, autocomplete: "off", required: true })),
            h("label", {}, "Account", accountSelect("to", users)),
            h("label", {}, "Amount", h("input", { name: "amount", class: "amount", inputmode: "decimal", placeholder: "0.00", required: true, autocomplete: "off" })),
        ],
        send: (f, key) => {
            adminToken = f.token.value;
            const amount = parseMinor(f.amount.value);
            if (!amount) throw new Error("Amount must be positive, at most two decimals.");
            return api("/funding", {
                method: "POST",
                headers: { "Idempotency-Key": key, "X-Admin-Token": adminToken },
                body: JSON.stringify({ toAccountId: f.to.value, amount }),
            });
        },
    });
    render(
        h("h1", {}, "Funding"),
        h("p", { class: "lede" }, "Moves money from the system funding account into a user account. Needs the admin token; the token stays in this tab's memory only."),
        form, out);
}

const routes = { overview, accounts, transfers, chain, funding };
const detail = { accounts: account, transfers: transfer };

function route() {
    const [, name = "overview", id] = location.hash.split("/");
    for (const a of document.querySelectorAll(".side a")) {
        if (a.dataset.route === name) a.setAttribute("aria-current", "page");
        else a.removeAttribute("aria-current");
    }
    const run = id && detail[name] ? () => detail[name](decodeURIComponent(id)) : routes[name] || overview;
    Promise.resolve().then(run).catch(failure);
}

function initTheme() {
    let saved = null;
    try { saved = localStorage.getItem("tally-theme"); } catch { }
    if (saved) document.documentElement.dataset.theme = saved;
    document.getElementById("theme").addEventListener("click", () => {
        const dark = document.documentElement.dataset.theme
            ? document.documentElement.dataset.theme === "dark"
            : matchMedia("(prefers-color-scheme: dark)").matches;
        const next = dark ? "light" : "dark";
        document.documentElement.dataset.theme = next;
        try { localStorage.setItem("tally-theme", next); } catch { }
    });
}

document.getElementById("seal").addEventListener("click", () => refreshSeal().catch(() => {}));
addEventListener("hashchange", route);
initTheme();
route();
refreshSeal().catch(() => {});

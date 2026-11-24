create table accounts (
    id         uuid primary key,
    owner_name text           not null check (length(owner_name) between 1 and 200),
    currency   char(3)        not null check (currency ~ '^[A-Z]{3}$'),
    kind       text           not null default 'USER' check (kind in ('USER', 'TREASURY')),
    balance    bigint         not null default 0,
    created_at timestamptz(6) not null default now(),
    -- Only the treasury may go negative: it is where money enters the ledger.
    constraint accounts_balance_non_negative check (kind = 'TREASURY' or balance >= 0)
);

create unique index accounts_one_treasury_per_currency on accounts (currency) where kind = 'TREASURY';

create table transfers (
    id           uuid primary key,
    from_account uuid           not null references accounts (id),
    to_account   uuid           not null references accounts (id),
    amount       bigint         not null check (amount > 0),
    currency     char(3)        not null,
    status       text           not null check (status in ('COMMITTED')),
    created_at   timestamptz(6) not null,
    constraint transfers_distinct_accounts check (from_account <> to_account)
);

create table ledger_entries (
    id          bigserial primary key,
    transfer_id uuid           not null references transfers (id),
    account_id  uuid           not null references accounts (id),
    amount      bigint         not null check (amount > 0),
    direction   char(1)        not null check (direction in ('D', 'C')),
    created_at  timestamptz(6) not null,
    constraint ledger_entries_one_per_side unique (transfer_id, direction)
);

create index ledger_entries_account_recent on ledger_entries (account_id, id desc);

create table idempotency_keys (
    key          text           primary key check (length(key) between 1 and 64),
    request_hash bytea          not null check (length(request_hash) = 32),
    -- Deferred: the key is claimed before the transfer row exists, both commit together.
    transfer_id  uuid           not null references transfers (id) deferrable initially deferred,
    created_at   timestamptz(6) not null default now()
);

create table ledger_events (
    seq         bigint         primary key check (seq > 0),
    transfer_id uuid           not null unique references transfers (id),
    payload     bytea          not null,
    prev_hash   bytea          not null check (length(prev_hash) = 32),
    hash        bytea          not null unique check (length(hash) = 32),
    created_at  timestamptz(6) not null
);

create table chain_head (
    id   smallint primary key check (id = 1),
    seq  bigint   not null,
    hash bytea    not null check (length(hash) = 32)
);

insert into chain_head (id, seq, hash) values (1, 0, decode(repeat('00', 32), 'hex'));

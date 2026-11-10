do $$
begin
    if not exists (select from pg_roles where rolname = 'tally_app') then
        create role tally_app login;
    end if;
    if not exists (select from pg_roles where rolname = 'tally_verifier') then
        create role tally_verifier login;
    end if;
end
$$;

-- Passwords come from Flyway placeholders, never from the repo; empty means managed elsewhere (CNPG, Vault).
do $$
begin
    if '${app_password}' <> '' then
        execute format('alter role tally_app password %L', '${app_password}');
    end if;
    if '${verifier_password}' <> '' then
        execute format('alter role tally_verifier password %L', '${verifier_password}');
    end if;
end
$$;

grant usage on schema public to tally_app, tally_verifier;

grant select, insert on accounts, transfers, ledger_entries, idempotency_keys, ledger_events, chain_head to tally_app;
grant update on accounts, transfers, chain_head to tally_app;
grant usage on sequence ledger_entries_id_seq to tally_app;

grant select on accounts, transfers, ledger_entries, idempotency_keys, ledger_events, chain_head to tally_verifier;

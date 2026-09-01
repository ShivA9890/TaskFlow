create table organizations (
    id         uuid primary key,
    name       varchar(120) not null,
    created_at timestamptz  not null default now()
);

create table users (
    id            uuid primary key,
    org_id        uuid         not null references organizations (id) on delete cascade,
    email         varchar(255) not null,
    password_hash varchar(100) not null,
    name          varchar(120) not null,
    role          varchar(16)  not null,
    status        varchar(16)  not null,
    timezone      varchar(64)  not null default 'UTC',
    created_at    timestamptz  not null default now(),
    constraint users_role_chk check (role in ('ADMIN', 'MEMBER')),
    constraint users_status_chk check (status in ('INVITED', 'ACTIVE', 'DISABLED'))
);

create unique index users_email_uk on users (lower(email));
create index users_org_idx on users (org_id);

create table teams (
    id         uuid primary key,
    org_id     uuid         not null references organizations (id) on delete cascade,
    name       varchar(120) not null,
    created_at timestamptz  not null default now()
);

create table team_members (
    team_id uuid not null references teams (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    primary key (team_id, user_id)
);

create table invites (
    id          uuid primary key,
    org_id      uuid         not null references organizations (id) on delete cascade,
    email       varchar(255) not null,
    token_hash  varchar(64)  not null,
    role        varchar(16)  not null,
    expires_at  timestamptz  not null,
    accepted_at timestamptz,
    created_at  timestamptz  not null default now(),
    constraint invites_role_chk check (role in ('ADMIN', 'MEMBER'))
);

create unique index invites_token_hash_uk on invites (token_hash);
create index invites_org_idx on invites (org_id);

create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    revoked    boolean     not null default false,
    created_at timestamptz not null default now()
);

create unique index refresh_tokens_hash_uk on refresh_tokens (token_hash);
create index refresh_tokens_user_idx on refresh_tokens (user_id);

create table outbox (
    id           uuid primary key,
    event_type   varchar(64) not null,
    payload      jsonb       not null,
    created_at   timestamptz not null default now(),
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (created_at) where published_at is null;

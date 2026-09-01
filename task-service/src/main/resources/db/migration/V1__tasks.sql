create table boards (
    id         uuid primary key,
    org_id     uuid         not null,
    team_id    uuid,
    name       varchar(120) not null,
    created_by uuid         not null,
    created_at timestamptz  not null default now()
);

create index boards_org_idx on boards (org_id);

create table board_columns (
    id          uuid primary key,
    board_id    uuid         not null references boards (id) on delete cascade,
    name        varchar(60)  not null,
    position    integer      not null,
    is_terminal boolean      not null default false,
    constraint board_columns_position_chk check (position >= 0)
);

create index board_columns_board_idx on board_columns (board_id, position);

-- Positions are swapped during reordering, so uniqueness is checked at commit
-- rather than per statement. Without DEFERRABLE, a swap needs a temporary
-- placeholder value just to get past the constraint.
alter table board_columns
    add constraint board_columns_position_uk unique (board_id, position)
        deferrable initially deferred;

-- Exactly one column can mark work as done.
create unique index board_columns_terminal_uk
    on board_columns (board_id) where is_terminal;

create table tasks (
    id           uuid primary key,
    org_id       uuid             not null,
    board_id     uuid             not null references boards (id) on delete cascade,
    column_id    uuid             not null references board_columns (id),
    title        varchar(200)     not null,
    body         text             not null default '',
    assignee_id  uuid,
    reporter_id  uuid             not null,
    severity     varchar(16)      not null,
    due_date     timestamptz,
    position     double precision not null,
    moved_at     timestamptz      not null default now(),
    completed_at timestamptz,
    created_at   timestamptz      not null default now(),
    updated_at   timestamptz      not null default now(),
    version      bigint           not null default 0,
    constraint tasks_severity_chk
        check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

create index tasks_column_idx on tasks (column_id, position);
create index tasks_board_idx on tasks (board_id);
create index tasks_assignee_idx on tasks (assignee_id) where assignee_id is not null;

-- Serves the reminder job. Only open, dated tasks are candidates, so the index
-- stays small however much completed work piles up.
create index tasks_open_due_idx on tasks (due_date)
    where completed_at is null and due_date is not null;

create table outbox (
    id           uuid primary key,
    event_type   varchar(64) not null,
    payload      jsonb       not null,
    created_at   timestamptz not null default now(),
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (created_at) where published_at is null;

-- The 3-to-6 column rule. A constraint trigger fires at COMMIT, not per row,
-- which is what makes it workable: creating a board inserts columns one at a
-- time and would trip a per-statement minimum on the very first insert.
create or replace function enforce_board_column_bounds() returns trigger as
$$
declare
    target_board uuid;
    column_count integer;
begin
    target_board := coalesce(new.board_id, old.board_id);

    -- The board itself may have been deleted in this transaction; cascade
    -- removed its columns and there is nothing left to validate.
    if not exists (select 1 from boards where id = target_board) then
        return null;
    end if;

    select count(*) into column_count
    from board_columns
    where board_id = target_board;

    if column_count < 3 then
        raise exception 'A board needs at least 3 columns.';
    end if;
    if column_count > 6 then
        raise exception 'A board can have at most 6 columns.';
    end if;

    return null;
end;
$$ language plpgsql;

create constraint trigger board_column_bounds
    after insert or delete
    on board_columns
    deferrable initially deferred
    for each row
execute function enforce_board_column_bounds();

create table reservation_link (
    id          serial primary key not null,
    token       varchar(36) not null,
    event_id    integer not null,
    category_id integer not null,
    quantity    integer not null check (quantity >= 1),
    expires_at  timestamp with time zone not null,
    status      varchar(16) not null default 'ACTIVE',
    created_at  timestamp with time zone not null default now()
);

alter table reservation_link add constraint reservation_link_event_fk foreign key (event_id) references event(id);
alter table reservation_link add constraint reservation_link_category_fk foreign key (category_id) references ticket_category(id);
create unique index reservation_link_token_idx on reservation_link(token);
create index reservation_link_category_idx on reservation_link(category_id);

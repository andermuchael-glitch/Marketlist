create table if not exists public.shopping_lists (
    id uuid primary key default gen_random_uuid(),
    name text not null default 'Minha compra',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table if not exists public.shopping_items (
    id uuid primary key default gen_random_uuid(),
    list_id uuid not null references public.shopping_lists(id) on delete cascade,
    name text not null,
    price numeric(12,2) not null default 0,
    quantity integer not null default 1 check (quantity > 0),
    bought boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
alter table public.shopping_lists enable row level security;
alter table public.shopping_items enable row level security;
-- Antes de liberar escrita, configure Auth + RLS por usuário.
